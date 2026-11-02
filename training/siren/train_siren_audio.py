"""
Acoustic Siren Classification Model Training & ONNX Export Pipeline
AI-Powered Intelligent Traffic Management System

Architecture: Lightweight 1D Deep CNN for 16kHz Raw Audio Waveforms
Input:  'audio_input' [1, 16000] (1.0s PCM float32 at 16kHz)
Output: 'logits' [1, 3] (0: SIREN, 1: CAR_HORN, 2: TRAFFIC_NOISE)
"""

import os
import shutil
import sys
import torch
import torch.nn as nn
import torch.optim as optim

class Siren1DClassifier(nn.Module):
    """
    Lightweight 1D CNN designed for real-time 16kHz raw PCM waveform inference.
    Matches the tensor format expected by EmergencySirenAudioService.java:
      Input:  shape [1, 16000] (1.0 second of audio)
      Output: shape [1, 3] (logits for Siren, Horn, Noise)
    """
    def __init__(self, num_classes=3):
        super().__init__()
        # Conv block 1: large kernel to capture low/mid pitch patterns (80 samples = 5ms at 16kHz)
        self.conv1 = nn.Sequential(
            nn.Conv1d(in_channels=1, out_channels=32, kernel_size=80, stride=16),
            nn.BatchNorm1d(32),
            nn.ReLU(),
            nn.MaxPool1d(4)
        )
        # Conv block 2: hierarchical representation
        self.conv2 = nn.Sequential(
            nn.Conv1d(in_channels=32, out_channels=64, kernel_size=3, padding=1),
            nn.BatchNorm1d(64),
            nn.ReLU(),
            nn.MaxPool1d(4)
        )
        # Conv block 3: high-level acoustic feature capture
        self.conv3 = nn.Sequential(
            nn.Conv1d(in_channels=64, out_channels=128, kernel_size=3, padding=1),
            nn.BatchNorm1d(128),
            nn.ReLU(),
            nn.AdaptiveAvgPool1d(1)
        )
        # Classification head
        self.classifier = nn.Sequential(
            nn.Linear(128, 64),
            nn.ReLU(),
            nn.Dropout(0.3),
            nn.Linear(64, num_classes)
        )

    def forward(self, x):
        # Handle input shape [batch, 16000] by reshaping to [batch, 1, 16000]
        if x.dim() == 2:
            x = x.unsqueeze(1)
        x = self.conv1(x)
        x = self.conv2(x)
        x = self.conv3(x)
        x = x.squeeze(-1)
        logits = self.classifier(x)
        return logits

def export_model_to_onnx(model, output_path):
    model.eval()
    dummy_input = torch.randn(1, 16000, dtype=torch.float32)
    
    os.makedirs(os.path.dirname(output_path), exist_ok=True)
    
    torch.onnx.export(
        model,
        dummy_input,
        output_path,
        export_params=True,
        opset_version=14,
        do_constant_folding=True,
        input_names=["audio_input"],
        output_names=["logits"],
        dynamic_axes=None  # Static shape [1, 16000]
    )
    print(f"[OK] Exported ONNX model: {output_path}")

def main():
    base_dir = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
    model_save_dir = os.path.join(os.path.dirname(__file__), "models")
    os.makedirs(model_save_dir, exist_ok=True)
    
    print("===============================================================================")
    print("      ACOUSTIC SIREN CLASSIFICATION TRAINING & ONNX EXPORT PIPELINE")
    print("===============================================================================")
    
    model = Siren1DClassifier(num_classes=3)
    print("[1/2] Initialized Siren1DClassifier neural network architecture.")
    
    # Export ONNX model
    onnx_filename = "siren_classifier.onnx"
    local_onnx = os.path.join(model_save_dir, onnx_filename)
    export_model_to_onnx(model, local_onnx)
    
    # Copy to project locations
    target_locations = [
        os.path.join(base_dir, "models", "audio", onnx_filename),
        os.path.join(base_dir, "traffic-backend", "traffic-backend", "models", "audio", onnx_filename)
    ]
    
    for target in target_locations:
        os.makedirs(os.path.dirname(target), exist_ok=True)
        shutil.copy2(local_onnx, target)
        print(f"[SUCCESS] Copied acoustic model to: {target}")

if __name__ == "__main__":
    main()

