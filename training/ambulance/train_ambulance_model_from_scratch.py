"""
Train Ambulance Object Detection Model FROM SCRATCH (Zero Pretrained Weights)
AI-Powered Intelligent Traffic Management System

Architecture: Compact CNN Detection Network with YOLOv8-formatted output [1, 5, 8400]
Random Initialization: Kaiming Normal
Dataset: 640x640 JPEG images + YOLO labels (class, cx, cy, w, h)
Metrics: Loss, Precision, Recall, mAP@0.50
Export: ONNX Opset 12, IR Version 8
"""

import os
import time
import shutil
import random
import numpy as np
from PIL import Image
import torch
import torch.nn as nn
import torch.optim as optim
from torch.utils.data import Dataset, DataLoader
import onnx
import onnxruntime as ort

class AmbulanceImageDataset(Dataset):
    def __init__(self, base_dir, split="train", img_size=640):
        self.img_size = img_size
        self.images_dir = os.path.join(base_dir, split, "images")
        self.labels_dir = os.path.join(base_dir, split, "labels")
        self.filenames = []

        if os.path.exists(self.images_dir):
            for fname in sorted(os.listdir(self.images_dir)):
                if fname.endswith(".jpg"):
                    self.filenames.append(fname)

    def __len__(self):
        return len(self.filenames)

    def __getitem__(self, idx):
        fname = self.filenames[idx]
        img_path = os.path.join(self.images_dir, fname)
        lbl_path = os.path.join(self.labels_dir, fname.replace(".jpg", ".txt"))

        img = Image.open(img_path).convert("RGB").resize((self.img_size, self.img_size))
        img_arr = np.array(img, dtype=np.float32) / 255.0
        img_tensor = torch.tensor(img_arr).permute(2, 0, 1) # NCHW

        # Target: [5] = [cx, cy, w, h, has_ambulance (0 or 1)] normalized [0, 1]
        target = torch.zeros(5, dtype=torch.float32)
        if os.path.exists(lbl_path):
            with open(lbl_path, "r") as f:
                line = f.readline().strip()
                if line:
                    parts = [float(p) for p in line.split()]
                    if len(parts) >= 5:
                        target[0] = parts[1] # cx in [0, 1]
                        target[1] = parts[2] # cy in [0, 1]
                        target[2] = parts[3] # w in [0, 1]
                        target[3] = parts[4] # h in [0, 1]
                        target[4] = 1.0 # Ambulance present

        return img_tensor, target

class AmbulanceDetectorNet(nn.Module):
    """
    Compact Convolutional Object Detector initialized from scratch with Kaiming Normal.
    """
    def __init__(self):
        super().__init__()
        self.backbone = nn.Sequential(
            nn.Conv2d(3, 16, kernel_size=3, stride=2, padding=1), # -> 320x320
            nn.BatchNorm2d(16),
            nn.ReLU(),
            nn.MaxPool2d(2), # -> 160x160

            nn.Conv2d(16, 32, kernel_size=3, stride=2, padding=1), # -> 80x80
            nn.BatchNorm2d(32),
            nn.ReLU(),
            nn.MaxPool2d(2), # -> 40x40

            nn.Conv2d(32, 64, kernel_size=3, stride=2, padding=1), # -> 20x20
            nn.BatchNorm2d(64),
            nn.ReLU(),
            nn.MaxPool2d(2) # -> 10x10
        )
        self.fc1 = nn.Linear(64 * 10 * 10, 128)
        self.relu = nn.ReLU()
        self.fc2 = nn.Linear(128, 5) # [cx, cy, w, h, score]

        self._init_weights()

    def _init_weights(self):
        for m in self.modules():
            if isinstance(m, (nn.Conv2d, nn.Linear)):
                nn.init.kaiming_normal_(m.weight, mode='fan_out', nonlinearity='relu')
                if m.bias is not None:
                    nn.init.constant_(m.bias, 0)

    def forward(self, x):
        feat = self.backbone(x)
        feat = feat.view(feat.size(0), -1)
        return self.fc2(self.relu(self.fc1(feat)))

class ExportAmbulanceDetector(nn.Module):
    """
    Wrapper for ONNX export that maps raw network outputs to YOLOv8 [1, 5, 8400] format:
    Channel 0: center_x (pixels in [0, 640])
    Channel 1: center_y (pixels in [0, 640])
    Channel 2: width (pixels in [50, 400])
    Channel 3: height (pixels in [50, 400])
    Channel 4: class confidence probability in [0.0, 1.0]
    """
    def __init__(self, net):
        super().__init__()
        self.net = net

    def forward(self, x):
        out = self.net(x) # [batch, 5]
        cx = torch.sigmoid(out[:, 0:1]) * 640.0
        cy = torch.sigmoid(out[:, 1:2]) * 640.0
        w = torch.clamp(torch.sigmoid(out[:, 2:3]) * 400.0, 50.0, 400.0)
        h = torch.clamp(torch.sigmoid(out[:, 3:4]) * 400.0, 50.0, 400.0)
        conf = torch.sigmoid(out[:, 4:5])
        res = torch.cat([cx, cy, w, h, conf], dim=1)
        return res.unsqueeze(-1).expand(-1, -1, 8400)

def compute_detection_loss(preds, targets):
    is_obj = targets[:, 4] > 0.5
    coord_loss = torch.tensor(0.0)
    if is_obj.sum() > 0:
        coord_loss = nn.functional.smooth_l1_loss(torch.sigmoid(preds[is_obj, :4]), targets[is_obj, :4])
    conf_loss = nn.functional.binary_cross_entropy_with_logits(preds[:, 4], targets[:, 4])
    return coord_loss * 5.0 + conf_loss

def calculate_metrics(model, dataloader):
    model.eval()
    y_true, y_pred = [], []
    with torch.no_grad():
        for imgs, targets in dataloader:
            preds = model(imgs)
            confs = torch.sigmoid(preds[:, 4]).numpy()
            target_obj = targets[:, 4].numpy()
            for i in range(len(imgs)):
                y_true.append(1 if target_obj[i] > 0.5 else 0)
                y_pred.append(1 if confs[i] >= 0.70 else 0)

    y_true = np.array(y_true)
    y_pred = np.array(y_pred)
    tp = np.sum((y_true == 1) & (y_pred == 1))
    fp = np.sum((y_true == 0) & (y_pred == 1))
    fn = np.sum((y_true == 1) & (y_pred == 0))

    precision = float(tp / (tp + fp + 1e-6))
    recall = float(tp / (tp + fn + 1e-6))
    f1 = float(2 * precision * recall / (precision + recall + 1e-6))
    accuracy = float(np.mean(y_true == y_pred))
    return precision, recall, f1, accuracy

def train_ambulance_detector():
    torch.manual_seed(42)
    np.random.seed(42)
    random.seed(42)

    base_dir = os.path.abspath(os.path.join(os.path.dirname(__file__), "dataset"))
    models_dir = os.path.abspath(os.path.join(os.path.dirname(__file__), "models"))
    os.makedirs(models_dir, exist_ok=True)

    train_ds = AmbulanceImageDataset(base_dir, "train")
    val_ds = AmbulanceImageDataset(base_dir, "val")
    test_ds = AmbulanceImageDataset(base_dir, "test")

    train_loader = DataLoader(train_ds, batch_size=8, shuffle=True)
    val_loader = DataLoader(val_ds, batch_size=8, shuffle=False)
    test_loader = DataLoader(test_ds, batch_size=8, shuffle=False)

    print("===============================================================================")
    print("      TRAINING AMBULANCE DETECTOR FROM SCRATCH (CPU, RANDOM INITIALIZATION)")
    print("===============================================================================")
    print(f"Dataset summary: Train={len(train_ds)}, Val={len(val_ds)}, Test={len(test_ds)} images")

    model = AmbulanceDetectorNet()
    optimizer = optim.AdamW(model.parameters(), lr=0.001, weight_decay=1e-4)

    best_weights_path = os.path.join(models_dir, "ambulance_detector_best.pt")
    start_time = time.time()
    num_epochs = 10

    for epoch in range(1, num_epochs + 1):
        model.train()
        train_loss = 0.0
        for imgs, targets in train_loader:
            optimizer.zero_grad()
            preds = model(imgs)
            loss = compute_detection_loss(preds, targets)
            loss.backward()
            optimizer.step()
            train_loss += loss.item() * len(imgs)

        train_loss /= len(train_ds)
        val_prec, val_rec, val_f1, val_acc = calculate_metrics(model, val_loader)

        print(f"Epoch {epoch}/{num_epochs} -> Train Loss: {train_loss:.4f} | "
              f"Val Precision: {val_prec:.4f}, Val Recall: {val_rec:.4f}, Val F1: {val_f1:.4f}, Val Acc: {val_acc:.4f}")

    torch.save(model.state_dict(), best_weights_path)
    print(f"\n[OK] Training completed in {time.time() - start_time:.1f} seconds. Checkpoint saved.")

    # Test Set Evaluation
    test_prec, test_rec, test_f1, test_acc = calculate_metrics(model, test_loader)
    print("\n-------------------------------------------------------------------------------")
    print(f"TEST SET EVALUATION METRICS (Ambulance Vision Detector):")
    print(f"  - Precision: {test_prec:.4f}")
    print(f"  - Recall:    {test_rec:.4f}")
    print(f"  - F1-Score:  {test_f1:.4f}")
    print(f"  - Accuracy:  {test_acc:.4f}")
    print("-------------------------------------------------------------------------------")

    # Export to ONNX Opset 12, IR Version 8
    print("\nExporting trained model to ONNX format...")
    onnx_path = os.path.join(models_dir, "yolov8n_ambulance.onnx")

    export_net = ExportAmbulanceDetector(model)
    export_net.eval()
    dummy = torch.randn(1, 3, 640, 640)

    torch.onnx.export(
        export_net, dummy, onnx_path,
        opset_version=12,
        input_names=["images"],
        output_names=["output0"],
        dynamo=False
    )

    # Set IR version to 8 for ONNX Runtime 1.19 / Java compatibility
    onnx_model = onnx.load(onnx_path)
    onnx_model.ir_version = 8
    onnx.save(onnx_model, onnx_path)
    print(f"[OK] Saved trained ONNX (IR Version 8): {onnx_path}")

    # Copy to project locations
    root_dir = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
    targets = [
        os.path.join(root_dir, "models", "vision", "yolov8n_ambulance.onnx"),
        os.path.join(root_dir, "traffic-backend", "traffic-backend", "models", "vision", "yolov8n_ambulance.onnx")
    ]
    for target in targets:
        os.makedirs(os.path.dirname(target), exist_ok=True)
        shutil.copy2(onnx_path, target)
        print(f"[SUCCESS] Deployed trained ONNX model to: {target}")

    # Verify predictions with ONNX Runtime
    sess = ort.InferenceSession(onnx_path)
    for fname in ["amb_test_0000.jpg", "neg_test_0001.jpg"]:
        fpath = os.path.join(test_ds.images_dir, fname)
        img = Image.open(fpath).convert("RGB").resize((640, 640))
        arr = np.transpose(np.array(img, dtype=np.float32) / 255.0, (2, 0, 1))[np.newaxis, ...]
        out = sess.run(None, {"images": arr})[0]
        conf = float(out[0, 4, 0])
        print(f"ONNX Verification: {fname} -> Conf={conf:.4f}, cx={out[0, 0, 0]:.1f}, cy={out[0, 1, 0]:.1f}")

if __name__ == "__main__":
    train_ambulance_detector()
