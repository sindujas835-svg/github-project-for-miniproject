"""
Balanced Multi-Class Traffic & Perception Detector Training
- Positive/Negative anchor balancing (prevents negative anchor dominance)
- Multi-threaded CPU execution (torch.set_num_threads(4))
- Trains in 4 epochs
- Exports with dynamo=False to models/vision/yolov8n_ambulance.onnx
"""

import os
import glob
import random
import numpy as np
import torch
import torch.nn as nn
import torch.optim as optim
from PIL import Image

# Use all CPU cores
torch.set_num_threads(os.cpu_count() or 4)
torch.manual_seed(42)
np.random.seed(42)
random.seed(42)

CLASSES = ["ambulance", "person", "car", "bus", "truck", "motorcycle"]
NUM_CLASSES = len(CLASSES)

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

def load_data_in_memory(base_dir):
    img_files = sorted(glob.glob(os.path.join(base_dir, "train", "images", "*.jpg")))
    lbl_dir = os.path.join(base_dir, "train", "labels")
    N = len(img_files)
    print(f"Pre-loading {N} training images into memory...", flush=True)

    images = torch.empty((N, 3, 640, 640), dtype=torch.uint8)
    cls_ids = torch.empty((N,), dtype=torch.long)
    bboxes = torch.empty((N, 4), dtype=torch.float32)

    for i, path in enumerate(img_files):
        img = Image.open(path).convert("RGB")
        arr = np.array(img, dtype=np.uint8)
        images[i] = torch.tensor(arr).permute(2, 0, 1)

        base_name = os.path.splitext(os.path.basename(path))[0]
        lbl_path = os.path.join(lbl_dir, base_name + ".txt")

        cid = -1
        bbox = [0.0, 0.0, 0.0, 0.0]
        if os.path.exists(lbl_path):
            with open(lbl_path, "r") as f:
                lines = f.readlines()
                if lines:
                    parts = lines[0].strip().split()
                    if len(parts) == 5:
                        cid = int(parts[0])
                        bbox = [float(p) for p in parts[1:5]]

        cls_ids[i] = cid
        bboxes[i] = torch.tensor(bbox)

    print("Pre-loading complete!", flush=True)
    return images, cls_ids, bboxes

def train_and_export():
    base_dir = r"c:\Users\shri\Downloads\traffic-routing-prototype\training\multiclass_vision\dataset"
    images, cls_ids, bboxes = load_data_in_memory(base_dir)
    N = images.shape[0]

    model = MultiClassDetector(num_classes=NUM_CLASSES)
    optimizer = optim.AdamW(model.parameters(), lr=0.008, weight_decay=1e-4)
    bce = nn.BCELoss()
    smooth_l1 = nn.SmoothL1Loss()

    batch_size = 32
    epochs = 4
    num_batches = (N + batch_size - 1) // batch_size
    print(f"Starting balanced training for {epochs} epochs on {torch.get_num_threads()} CPU threads...", flush=True)

    for epoch in range(1, epochs + 1):
        model.train()
        total_loss = 0.0
        perm = torch.randperm(N)

        for b_i in range(num_batches):
            idx_range = perm[b_i * batch_size : min((b_i + 1) * batch_size, N)]
            B = len(idx_range)
            if B == 0: continue

            b_imgs = images[idx_range].to(torch.float32) / 255.0
            b_cids = cls_ids[idx_range]
            b_boxes = bboxes[idx_range]

            optimizer.zero_grad()
            preds = model(b_imgs)

            pred_boxes = preds[:, 0:4, :] # [B, 4, 8400]
            pred_probs = preds[:, 4:10, :] # [B, 6, 8400]

            loss_pos = torch.tensor(0.0)
            loss_neg = torch.tensor(0.0)
            loss_box = torch.tensor(0.0)
            pos_count = 0

            for b in range(B):
                cid = b_cids[b].item()
                if cid >= 0:
                    cx, cy, w, h = b_boxes[b].tolist()
                    px_cx, px_cy = cx * 640.0, cy * 640.0
                    px_w, px_h = w * 640.0, h * 640.0

                    gx = int(cx * 40.0)
                    gy = int(cy * 40.0)
                    idx = 6400 + (gy * 40 + gx)
                    idx = min(idx, 8399)

                    # Positive anchor: target 1.0 for class cid, 0.0 for others
                    target_p = torch.zeros(NUM_CLASSES)
                    target_p[cid] = 1.0
                    loss_pos = loss_pos + bce(pred_probs[b, :, idx], target_p)

                    # Box loss
                    target_b = torch.tensor([px_cx, px_cy, px_w, px_h])
                    loss_box = loss_box + smooth_l1(pred_boxes[b, :, idx], target_b)
                    pos_count += 1

                # Sample 20 random negative anchors for background suppression
                neg_indices = torch.randint(0, 8400, (20,))
                zero_target = torch.zeros((20, NUM_CLASSES))
                neg_preds = pred_probs[b, :, neg_indices].t() # [20, 6]
                loss_neg = loss_neg + bce(neg_preds, zero_target)

            if pos_count > 0:
                loss_pos = loss_pos / pos_count
                loss_box = loss_box / pos_count
            loss_neg = loss_neg / B

            loss = loss_pos * 8.0 + loss_neg * 1.5 + loss_box * 0.04
            loss.backward()
            optimizer.step()
            total_loss += loss.item()

        avg_loss = total_loss / num_batches
        print(f"Epoch {epoch:02d}/{epochs:02d} - Loss: {avg_loss:.4f}", flush=True)

    # Evaluation on all 6 classes
    model.eval()
    print("\nEvaluating on sample test images across all classes:", flush=True)
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

            status = "CORRECT" if CLASSES[best_cls] == expected_cls else "MISMATCH"
            print(f"  [{status}] Target: {expected_cls:10s} -> Predicted: {CLASSES[best_cls]:10s} (Conf: {best_score*100:.1f}%) | Ambulance Conf: {amb_score*100:.1f}%", flush=True)

    # Export to ONNX with dynamo=False
    print("\nExporting trained model to ONNX format...", flush=True)
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
            dynamo=False,
            opset_version=12,
            do_constant_folding=True
        )
        print(f"Saved ONNX model successfully to: {p}", flush=True)

if __name__ == "__main__":
    train_and_export()

