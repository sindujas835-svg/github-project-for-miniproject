"""
Train Acoustic Siren Classification Model FROM SCRATCH (Zero Pretrained Weights)
AI-Powered Intelligent Traffic Management System

Random Initialization: Kaiming Normal
Dataset: 16kHz 1-second WAV audio clips (Siren vs Car Horn & Traffic Noise)
Metrics: Loss, Accuracy, Precision, Recall, F1-Score
Export: ONNX Opset 14, IR Version 8
"""

import os
import time
import shutil
import numpy as np
import soundfile as sf
import torch
import torch.nn as nn
import torch.optim as optim
from torch.utils.data import Dataset, DataLoader
import onnx
from onnx import helper, TensorProto, numpy_helper

class SirenWavDataset(Dataset):
    def __init__(self, base_dir, split="train"):
        self.samples = []
        siren_dir = os.path.join(base_dir, split, "siren")
        neg_dir = os.path.join(base_dir, split, "negative")

        if os.path.exists(siren_dir):
            for fname in os.listdir(siren_dir):
                if fname.endswith(".wav"):
                    self.samples.append((os.path.join(siren_dir, fname), 0)) # Class 0: Siren

        if os.path.exists(neg_dir):
            for fname in os.listdir(neg_dir):
                if fname.endswith(".wav"):
                    # Class 1: Car horn, Class 2: Traffic noise
                    cls_id = 1 if "car" in fname.lower() or "horn" in fname.lower() else 2
                    self.samples.append((os.path.join(neg_dir, fname), cls_id))

    def __len__(self):
        return len(self.samples)

    def __getitem__(self, idx):
        path, label = self.samples[idx]
        audio, sr = sf.read(path, dtype='float32')
        if len(audio) < 16000:
            audio = np.pad(audio, (0, 16000 - len(audio)))
        else:
            audio = audio[:16000]
        return torch.tensor(audio, dtype=torch.float32), torch.tensor(label, dtype=torch.long)

class SirenClassifierNet(nn.Module):
    def __init__(self, num_classes=3):
        super().__init__()
        self.conv1 = nn.Conv1d(1, 32, kernel_size=80, stride=16)
        self.bn1 = nn.BatchNorm1d(32)
        self.relu = nn.ReLU()
        self.pool1 = nn.MaxPool1d(4)
        
        self.conv2 = nn.Conv1d(32, 64, kernel_size=3, padding=1)
        self.bn2 = nn.BatchNorm1d(64)
        self.pool2 = nn.MaxPool1d(4)

        self.conv3 = nn.Conv1d(64, 64, kernel_size=3, padding=1)
        self.bn3 = nn.BatchNorm1d(64)
        
        self.fc = nn.Linear(64, num_classes)
        self._init_weights()

    def _init_weights(self):
        for m in self.modules():
            if isinstance(m, nn.Conv1d):
                nn.init.kaiming_normal_(m.weight, mode='fan_out', nonlinearity='relu')
                if m.bias is not None:
                    nn.init.constant_(m.bias, 0)
            elif isinstance(m, nn.Linear):
                nn.init.kaiming_normal_(m.weight, mode='fan_out', nonlinearity='relu')
                nn.init.constant_(m.bias, 0)

    def forward(self, x):
        if x.dim() == 2:
            x = x.unsqueeze(1)
        x = self.pool1(self.relu(self.bn1(self.conv1(x))))
        x = self.pool2(self.relu(self.bn2(self.conv2(x))))
        x = self.relu(self.bn3(self.conv3(x)))
        x = torch.mean(x, dim=-1)
        return self.fc(x)

def calculate_metrics(y_true, y_pred, positive_class=0):
    y_true = np.array(y_true)
    y_pred = np.array(y_pred)
    acc = np.mean(y_true == y_pred)
    
    tp = np.sum((y_true == positive_class) & (y_pred == positive_class))
    fp = np.sum((y_true != positive_class) & (y_pred == positive_class))
    fn = np.sum((y_true == positive_class) & (y_pred != positive_class))
    
    precision = tp / (tp + fp + 1e-6)
    recall = tp / (tp + fn + 1e-6)
    f1 = 2 * (precision * recall) / (precision + recall + 1e-6)
    return acc, precision, recall, f1

def train_siren_model():
    torch.manual_seed(42)
    np.random.seed(42)

    base_dir = os.path.abspath(os.path.join(os.path.dirname(__file__), "dataset"))
    models_dir = os.path.abspath(os.path.join(os.path.dirname(__file__), "models"))
    os.makedirs(models_dir, exist_ok=True)

    train_ds = SirenWavDataset(base_dir, "train")
    val_ds = SirenWavDataset(base_dir, "val")
    test_ds = SirenWavDataset(base_dir, "test")

    train_loader = DataLoader(train_ds, batch_size=16, shuffle=True)
    val_loader = DataLoader(val_ds, batch_size=16, shuffle=False)
    test_loader = DataLoader(test_ds, batch_size=16, shuffle=False)

    print("===============================================================================")
    print("      TRAINING SIREN CLASSIFIER FROM SCRATCH (CPU, RANDOM INITIALIZATION)")
    print("===============================================================================")
    print(f"Dataset summary: Train={len(train_ds)}, Val={len(val_ds)}, Test={len(test_ds)} samples")

    model = SirenClassifierNet(num_classes=3)
    criterion = nn.CrossEntropyLoss()
    optimizer = optim.Adam(model.parameters(), lr=0.002, weight_decay=1e-4)

    best_f1 = 0.0
    best_weights_path = os.path.join(models_dir, "siren_model_best.pt")

    start_time = time.time()
    num_epochs = 6

    for epoch in range(1, num_epochs + 1):
        # Training Phase
        model.train()
        train_loss = 0.0
        y_true_train, y_pred_train = [], []

        for x_batch, y_batch in train_loader:
            optimizer.zero_grad()
            logits = model(x_batch)
            loss = criterion(logits, y_batch)
            loss.backward()
            optimizer.step()

            train_loss += loss.item() * len(x_batch)
            preds = torch.argmax(logits, dim=1)
            y_true_train.extend(y_batch.numpy())
            y_pred_train.extend(preds.numpy())

        train_loss /= len(train_ds)
        t_acc, t_prec, t_rec, t_f1 = calculate_metrics(y_true_train, y_pred_train)

        # Validation Phase
        model.eval()
        val_loss = 0.0
        y_true_val, y_pred_val = [], []

        with torch.no_grad():
            for x_batch, y_batch in val_loader:
                logits = model(x_batch)
                loss = criterion(logits, y_batch)
                val_loss += loss.item() * len(x_batch)
                preds = torch.argmax(logits, dim=1)
                y_true_val.extend(y_batch.numpy())
                y_pred_val.extend(preds.numpy())

        val_loss /= len(val_ds)
        v_acc, v_prec, v_rec, v_f1 = calculate_metrics(y_true_val, y_pred_val)

        print(f"Epoch {epoch}/{num_epochs} -> Train Loss: {train_loss:.4f}, Acc: {t_acc*100:.1f}%, F1: {t_f1:.3f} | "
              f"Val Loss: {val_loss:.4f}, Val Acc: {v_acc*100:.1f}%, Val Prec: {v_prec:.3f}, Val Rec: {v_rec:.3f}, Val F1: {v_f1:.3f}")

        if v_f1 >= best_f1:
            best_f1 = v_f1
            torch.save(model.state_dict(), best_weights_path)
            print(f"  --> Saved new best checkpoint to {best_weights_path} (F1: {best_f1:.3f})")

    duration = time.time() - start_time
    print(f"\n[OK] Training completed in {duration:.1f} seconds.")

    # Final Evaluation on Test Set
    model.load_state_dict(torch.load(best_weights_path))
    model.eval()
    y_true_test, y_pred_test = [], []
    with torch.no_grad():
        for x_batch, y_batch in test_loader:
            logits = model(x_batch)
            preds = torch.argmax(logits, dim=1)
            y_true_test.extend(y_batch.numpy())
            y_pred_test.extend(preds.numpy())

    test_acc, test_prec, test_rec, test_f1 = calculate_metrics(y_true_test, y_pred_test)
    print("\n-------------------------------------------------------------------------------")
    print(f"TEST SET EVALUATION METRICS (Siren Audio Classifier):")
    print(f"  - Accuracy:  {test_acc * 100:.2f}%")
    print(f"  - Precision: {test_prec:.4f}")
    print(f"  - Recall:    {test_rec:.4f}")
    print(f"  - F1-Score:  {test_f1:.4f}")
    print("-------------------------------------------------------------------------------")

    # Export to ONNX (IR version 8, Opset 14)
    print("\nExporting trained PyTorch 1D-CNN directly to ONNX format...")
    onnx_path = os.path.join(models_dir, "siren_classifier.onnx")
    
    dummy_input = torch.randn(1, 16000, dtype=torch.float32)
    model.eval()
    
    torch.onnx.export(
        model, dummy_input, onnx_path,
        input_names=['audio_input'],
        output_names=['logits'],
        opset_version=14,
        dynamo=False,
        do_constant_folding=True
    )

    onnx_model = onnx.load(onnx_path)
    onnx_model.ir_version = 8
    onnx.checker.check_model(onnx_model)
    onnx.save(onnx_model, onnx_path)
    print(f"[OK] Saved verified trained ONNX model (IR-8): {onnx_path}")

    # Copy to project locations
    root_dir = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
    targets = [
        os.path.join(root_dir, "models", "audio", "siren_classifier.onnx"),
        os.path.join(root_dir, "traffic-backend", "traffic-backend", "models", "audio", "siren_classifier.onnx")
    ]
    for target in targets:
        os.makedirs(os.path.dirname(target), exist_ok=True)
        shutil.copy2(onnx_path, target)
        print(f"[SUCCESS] Deployed trained ONNX model to: {target}")

if __name__ == "__main__":
    train_siren_model()

