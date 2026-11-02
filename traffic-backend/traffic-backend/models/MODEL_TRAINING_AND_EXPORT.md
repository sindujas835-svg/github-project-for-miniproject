# ONNX Model Acquisition, Training & Export Guide
### AI-Powered Intelligent Traffic Management System

This guide explains how to acquire, train, and export compatible ONNX models for:
1. **Ambulance Vision Detection** (`models/vision/yolov8n_ambulance.onnx`)
2. **Emergency Siren Acoustic Classification** (`models/audio/siren_classifier.onnx`)

---

## 1. Ambulance Vision Model (YOLOv8-Nano / RF-DETR)

### A. Dataset Recommendations (with Indian Road Examples)
- **Roboflow Universe - Ambulance Dataset:** Contains over 4,500 annotated ambulance images across varying lighting and weather conditions.
- **Indian Driving Dataset (IDD):** Contains rich annotations for complex Indian traffic (autorickshaws, crowded intersections, pedestrians, ambulances).
- Search query on Roboflow: `ambulance-detection` or `emergency-vehicles`.

### B. Training Workflow via Ultralytics YOLOv8 (Free Google Colab T4 GPU)
```python
from ultralytics import YOLO

# 1. Load pretrained YOLOv8-Nano (lightweight, 3.2M parameters)
model = YOLO("yolov8n.pt")

# 2. Fine-tune on ambulance dataset
model.train(
    data="ambulance_dataset.yaml",
    epochs=50,
    imgsz=640,
    batch=16,
    device=0
)

# 3. Export to ONNX format (STRICT: opset=12, static shape)
model.export(
    format="onnx",
    imgsz=640,
    opset=12,
    dynamic=False,
    simplify=True
)
# Output: runs/detect/train/weights/best.onnx
```

### C. Placement in Project
Copy the exported `best.onnx` file to:
```
traffic-backend/traffic-backend/models/vision/yolov8n_ambulance.onnx
```

### D. Model Tensor Specifications
- **Input Tensor Name:** `images`
- **Input Shape:** `[1, 3, 640, 640]` (NCHW format, normalized float `0.0 - 1.0`, RGB channel order)
- **Output Tensor Name:** `output0`
- **Output Shape:** `[1, 4 + C, 8400]` (8400 candidate boxes: `[cx, cy, w, h, class_scores...]`)

---

## 2. Emergency Siren Acoustic Classification Model (E-PANNs / PANNs)

### A. Dataset Recommendations
- **AudioSet-EV (Emergency Vehicle Subset):** Curated subset of Google AudioSet containing over 3,000 labelled siren audio clips (`/m/03kmc9` Siren, `/m/012n7d` Ambulance siren).
- **ESC-50 (Environmental Sound Classification):** Class 42 contains high-quality emergency siren recordings.

### B. No-Code / MATLAB Audio Toolbox Workflow
If using MATLAB:
1. Load audio files using `audioDatastore`.
2. Extract Log-Mel Spectrogram features using `melSpectrogram`.
3. Train a lightweight 2D CNN (e.g. SqueezeNet or MobileNetV2 adapted for spectrograms).
4. Export directly to ONNX:
   ```matlab
   exportONNXNetwork(sirenNet, 'siren_classifier.onnx');
   ```

### C. PyTorch Training & Export Workflow
```python
import torch
import torch.nn as nn

# Lightweight 1D CNN for raw 16kHz audio or 2D CNN for Mel-Spectrograms
class SirenClassifier(nn.Module):
    def __init__(self):
        super().__init__()
        self.conv = nn.Sequential(
            nn.Conv1d(1, 32, kernel_size=80, stride=16),
            nn.BatchNorm1d(32),
            nn.ReLU(),
            nn.MaxPool1d(4),
            nn.Conv1d(32, 64, kernel_size=3),
            nn.BatchNorm1d(64),
            nn.ReLU(),
            nn.AdaptiveAvgPool1d(1)
        )
        self.fc = nn.Linear(64, 3) # [0: Siren, 1: Car Horn, 2: Traffic Noise]

    def forward(self, x):
        x = self.conv(x)
        x = x.squeeze(-1)
        return self.fc(x)

# Export to ONNX
model = SirenClassifier()
dummy_audio = torch.randn(1, 1, 16000) # 1.0 second of 16kHz PCM audio
torch.onnx.export(
    model,
    dummy_audio,
    "siren_classifier.onnx",
    input_names=["audio_input"],
    output_names=["logits"],
    opset_version=14
)
```

### D. Placement in Project
Copy the exported `siren_classifier.onnx` file to:
```
traffic-backend/traffic-backend/models/audio/siren_classifier.onnx
```

---

## 3. Engine Status & Deployed Weight Files

| Engine | Physical Hardware | AI Model File | Active Operational Mode |
|---|:---:|:---:|---|
| **Microphone / Audio** | **ACTIVE** (16kHz 16-bit mono) | **DEPLOYED** (`models/audio/siren_classifier.onnx`) | **ONNX Runtime Neural Inference + Physical Microphone DSP Spectral Analysis** (Hann Windowing + Radix-2 FFT + 650-1550Hz Energy Ratio on live microphone sound). |
| **Camera / Vision** | Probing (Webcam / USB) | **DEPLOYED** (`models/vision/yolov8n_ambulance.onnx`) | **ONNX Runtime YOLOv8 Object Detection + OpenCV Frame Pipeline** (Centroid Tracking across frames + Geometric RoadRoi approach mapping). |

---

## 4. Automation & Training Scripts in `training/`

- `training/export_initial_onnx_models.py`: Rapid generator that exports spec-compliant ONNX models with IR Version 8 and deploys them to backend paths.
- `training/ambulance/train_ambulance_yolo.py`: Ultralytics YOLOv8 training & fine-tuning pipeline on custom ambulance datasets.
- `training/siren/train_siren_audio.py`: PyTorch 1D CNN training pipeline for 16kHz audio waveforms and ONNX export.
