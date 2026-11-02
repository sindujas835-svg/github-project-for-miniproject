import os

dirs = [
    "training/ambulance/dataset/train/images",
    "training/ambulance/dataset/train/labels",
    "training/ambulance/dataset/val/images",
    "training/ambulance/dataset/val/labels",
    "training/ambulance/dataset/test/images",
    "training/ambulance/dataset/test/labels",
    "training/ambulance/models",
    "training/siren/dataset/train/siren",
    "training/siren/dataset/train/negative",
    "training/siren/dataset/val/siren",
    "training/siren/dataset/val/negative",
    "training/siren/dataset/test/siren",
    "training/siren/dataset/test/negative",
    "training/siren/models",
    "models/vision",
    "models/audio"
]

base_dir = os.path.abspath(os.path.dirname(__file__))

for d in dirs:
    path = os.path.join(base_dir, d)
    os.makedirs(path, exist_ok=True)
    print(f"Created: {path}")

print("All directories created successfully.")
