"""
High-Speed Multi-Class Traffic & Perception Detector Training
Trained strictly from scratch (Zero Pretrained Weights, Kaiming Normal Initialization).
Outputs standard YOLOv8 tensor shape [1, 10, 8400]:
  - 4 Box Coordinates (cx, cy, w, h) in 0..640 pixel coordinates
  - 6 Multi-Class Probabilities:
      0: ambulance
      1: person
      2: car
      3: bus
      4: truck
      5: motorcycle
"""

import os
import glob
import random
import numpy as np
import torch
import torch.nn as nn
import torch.optim as optim
from torch.utils.data import Dataset, DataLoader
from PIL import Image

torch.manual_seed(42)
np.random.seed(42)
random.seed(42)

CLASSES = ["ambulance", "person", "car", "bus", "truck", "motorcycle"]
NUM_CLASSES = len(CLASSES)

class MultiClassDataset(Dataset):
    def __init__(self, img_dir, lbl_dir):
        self.img_files = sorted(glob.glob(os.path.join(img_dir, "*.jpg")))
        self.lbl_dir = lbl_dir

    def __len__(self):
        return len(self.img_files)

    def __getitem__(self, idx):
        img_path = self.img_files[idx]
        img = Image.open(img_path).convert("RGB")
        img_arr = np.array(img, dtype=np.float32) / 255.0
        tensor_img = torch.tensor(img_arr).permute(2, 0, 1) # [3, 640, 640]

        base_name = os.path.splitext(os.path.basename(img_path))[0]
        lbl_path = os.path.join(self.lbl_dir, base_name + ".txt")

        cls_id = -1
        bbox = [0.0, 0.0, 0.0, 0.0]

        if os.path.exists(lbl_path):
            with open(lbl_path, "r") as f:
                lines = f.readlines()
                if lines:
                    parts = lines[0].strip().split()
                    if len(parts) == 5:
                        cls_id = int(parts[0])
                        bbox = [float(p) for p in parts[1:5]]

        return tensor_img, cls_id, torch.tensor(bbox, dtype=torch.float32)

class MultiClassDetector(nn.Module):
    def __init__(self, num_classes=6):
        super().__init__()
        self.num_classes = num_classes

        # High-speed PyTorch backbone: Stride-8 stem immediately maps 640x640 -> 80x80 (P3)
        self.stem = nn.Sequential(
            nn.Conv2d(3, 32, kernel_size=8, stride=8, bias=False),
            nn.BatchNorm2d(32),
            nn.SiLU(),
            nn.Conv2d(32, 48, kernel_size=3, padding=1, bias=False),
            nn.BatchNorm2d(48),
            nn.SiLU()
        ) # P3: [B, 48, 80, 80]

        self.p4_down = nn.Sequential(
            nn.Conv2d(48, 96, kernel_size=3, stride=2, padding=1, bias=False),
            nn.BatchNorm2d(96),
            nn.SiLU()
        ) # P4: [B, 96, 40, 40]

        self.p5_down = nn.Sequential(
            nn.Conv2d(96, 128, kernel_size=3, stride=2, padding=1, bias=False),
            nn.BatchNorm2d(128),
            nn.SiLU()
        ) # P5: [B, 128, 20, 20]

        out_features = 4 + num_classes # 10 channels
        self.head_p3 = nn.Conv2d(48, out_features, kernel_size=1)
        self.head_p4 = nn.Conv2d(96, out_features, kernel_size=1)
        self.head_p5 = nn.Conv2d(128, out_features, kernel_size=1)

        self._init_weights()

    def _init_weights(self):
        for m in self.modules():
            if isinstance(m, nn.Conv2d):
                nn.init.kaiming_normal_(m.weight, mode='fan_out', nonlinearity='relu')
                if m.bias is not None:
                    nn.init.constant_(m.bias, 0)
            elif isinstance(m, nn.BatchNorm2d):
                nn.init.constant_(m.weight, 1)
                nn.init.constant_(m.bias, 0)

    def forward(self, x):
        B = x.shape[0]

        p3 = self.stem(x)         # [B, 48, 80, 80]
        p4 = self.p4_down(p3)     # [B, 96, 40, 40]
        p5 = self.p5_down(p4)     # [B, 128, 20, 20]

        out3 = self.head_p3(p3).view(B, 10, -1) # [B, 10, 6400]
        out4 = self.head_p4(p4).view(B, 10, -1) # [B, 10, 1600]
        out5 = self.head_p5(p5).view(B, 10, -1) # [B, 10, 400]

        out = torch.cat([out3, out4, out5], dim=2) # [B, 10, 8400]

        box = torch.sigmoid(out[:, 0:4, :]) * 640.0
        probs = torch.sigmoid(out[:, 4:10, :])

        return torch.cat([box, probs], dim=1) # [B, 10, 8400]

def train_and_export():
    device = torch.device("cpu")
    print(f"Training MultiClassDetector on device: {device}", flush=True)

    base_dir = r"c:\Users\shri\Downloads\traffic-routing-prototype\training\multiclass_vision\dataset"
    train_dataset = MultiClassDataset(os.path.join(base_dir, "train", "images"), os.path.join(base_dir, "train", "labels"))
    train_loader = DataLoader(train_dataset, batch_size=32, shuffle=True, num_workers=0)

    model = MultiClassDetector(num_classes=NUM_CLASSES).to(device)
    optimizer = optim.AdamW(model.parameters(), lr=0.005, weight_decay=1e-4)
    cls_criterion = nn.BCELoss()
    box_criterion = nn.SmoothL1Loss()

    epochs = 10
    print(f"Starting fast training for {epochs} epochs from scratch...", flush=True)

    for epoch in range(1, epochs + 1):
        model.train()
        total_loss = 0.0

        for images, cls_ids, bboxes in train_loader:
            B = images.shape[0]
            optimizer.zero_grad()
            preds = model(images) # [B, 10, 8400]

            pred_boxes = preds[:, 0:4, :] # [B, 4, 8400]
            pred_probs = preds[:, 4:10, :] # [B, 6, 8400]

            target_probs = torch.zeros_like(pred_probs)
            target_boxes = torch.zeros_like(pred_boxes)
            pos_indices = []

            for b in range(B):
                cid = cls_ids[b].item()
                if cid >= 0:
                    cx, cy, w, h = bboxes[b].tolist()
                    px_cx, px_cy = cx * 640.0, cy * 640.0
                    px_w, px_h = w * 640.0, h * 640.0

                    gx = int(cx * 40.0)
                    gy = int(cy * 40.0)
                    idx = 6400 + (gy * 40 + gx)
                    idx = min(idx, 8399)

                    target_probs[b, cid, idx] = 1.0
                    target_boxes[b, :, idx] = torch.tensor([px_cx, px_cy, px_w, px_h])
                    pos_indices.append((b, idx))

            loss_cls = cls_criterion(pred_probs, target_probs)
            loss_box = torch.tensor(0.0)
            if pos_indices:
                b_idx = [p[0] for p in pos_indices]
                p_idx = [p[1] for p in pos_indices]
                loss_box = box_criterion(pred_boxes[b_idx, :, p_idx], target_boxes[b_idx, :, p_idx])

            loss = loss_cls * 20.0 + loss_box * 0.02
            loss.backward()
            optimizer.step()
            total_loss += loss.item()

        avg_loss = total_loss / len(train_loader)
        print(f"Epoch {epoch:02d}/{epochs:02d} - Loss: {avg_loss:.4f}", flush=True)

    # Evaluation
    model.eval()
    print("\nEvaluating trained model on representative test samples:", flush=True)
    test_files = [
        ("ambulance", glob.glob(os.path.join(base_dir, "test", "images", "ambulance*.jpg"))[0]),
        ("person", glob.glob(os.path.join(base_dir, "test", "images", "person*.jpg"))[0]),
        ("car", glob.glob(os.path.join(base_dir, "test", "images", "car*.jpg"))[0]),
        ("bus", glob.glob(os.path.join(base_dir, "test", "images", "bus*.jpg"))[0]),
        ("truck", glob.glob(os.path.join(base_dir, "test", "images", "truck*.jpg"))[0]),
        ("motorcycle", glob.glob(os.path.join(base_dir, "test", "images", "motorcycle*.jpg"))[0]),
    ]

    with torch.no_grad():
        for expected_cls, fpath in test_files:
            img = Image.open(fpath).convert("RGB")
            arr = np.array(img, dtype=np.float32) / 255.0
            t_img = torch.tensor(arr).permute(2, 0, 1).unsqueeze(0)

            out = model(t_img)
            probs = out[0, 4:10, :].numpy()

            max_p_per_candidate = np.max(probs, axis=0)
            best_idx = np.argmax(max_p_per_candidate)
            best_cls = np.argmax(probs[:, best_idx])
            best_score = probs[best_cls, best_idx]
            amb_score = probs[0, best_idx]

            print(f"  Target: {expected_cls:10s} -> Predicted: {CLASSES[best_cls]:10s} (Conf: {best_score*100:.1f}%) | Ambulance Conf: {amb_score*100:.1f}%", flush=True)

    # Export to ONNX
    print("\nExporting model to ONNX format...", flush=True)
    dummy_input = torch.randn(1, 3, 640, 640)
    export_paths = [
        r"c:\Users\shri\Downloads\traffic-routing-prototype\models\vision\yolov8n_ambulance.onnx",
        r"c:\Users\shri\Downloads\traffic-routing-prototype\traffic-backend\traffic-backend\models\vision\yolov8n_ambulance.onnx"
    ]

    for p in export_paths:
        os.makedirs(os.path.dirname(p), exist_ok=True)
        torch.onnx.export(
            model,
            dummy_input,
            p,
            input_names=["images"],
            output_names=["output0"],
            opset_version=12,
            do_constant_folding=True
        )
        print(f"Exported ONNX model successfully to: {p}", flush=True)

if __name__ == "__main__":
    train_and_export()
