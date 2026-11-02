"""
Ambulance Detection YOLOv8 Model Training & ONNX Export Pipeline
AI-Powered Intelligent Traffic Management System

This script trains/fine-tunes a YOLOv8-nano model on an ambulance detection dataset
and exports it to ONNX format matching the exact tensor shape required by the Java backend:
  - Input:  'images' [1, 3, 640, 640] normalized RGB float32
  - Output: 'output0' [1, 5, 8400] (cx, cy, w, h, confidence)
"""

import os
import shutil
import sys

def main():
    try:
        from ultralytics import YOLO
    except ImportError:
        print("[ERROR] ultralytics package is not installed.")
        print("Install via: pip install ultralytics")
        sys.exit(1)

    base_dir = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
    dataset_yaml = os.path.join(os.path.dirname(__file__), "ambulance_dataset.yaml")
    
    # Create dataset yaml if not exists
    if not os.path.exists(dataset_yaml):
        yaml_content = f"""# Ambulance Detection Dataset Configuration
path: {os.path.join(os.path.dirname(__file__), 'dataset').replace('\\', '/')}
train: images/train
val: images/val
test: images/test

names:
  0: ambulance
"""
        with open(dataset_yaml, "w") as f:
            f.write(yaml_content)
        print(f"[OK] Generated {dataset_yaml}")

    print("===============================================================================")
    print("      YOLOv8 AMBULANCE DETECTION TRAINING & ONNX EXPORT PIPELINE")
    print("===============================================================================")
    
    # 1. Load pretrained YOLOv8-Nano backbone
    print("\n[1/3] Loading YOLOv8-Nano pretrained weights...")
    model = YOLO("yolov8n.pt")

    # 2. Fine-tune on ambulance dataset
    print("\n[2/3] Starting model fine-tuning...")
    results = model.train(
        data=dataset_yaml,
        epochs=50,
        imgsz=640,
        batch=16,
        device=0 if os.environ.get("CUDA_VISIBLE_DEVICES") else "cpu",
        plots=True
    )

    # 3. Export to strict ONNX format
    print("\n[3/3] Exporting trained model to ONNX format (opset 12, static 640x640)...")
    exported_path = model.export(
        format="onnx",
        imgsz=640,
        opset=12,
        dynamic=False,
        simplify=True
    )

    # Copy to project locations
    target_locations = [
        os.path.join(base_dir, "models", "vision", "yolov8n_ambulance.onnx"),
        os.path.join(base_dir, "traffic-backend", "traffic-backend", "models", "vision", "yolov8n_ambulance.onnx")
    ]

    for target in target_locations:
        os.makedirs(os.path.dirname(target), exist_ok=True)
        shutil.copy2(exported_path, target)
        print(f"[SUCCESS] Exported and copied model to: {target}")

if __name__ == "__main__":
    main()

