"""
Multi-Class Vision Model Evaluation Script
Computes Precision, Recall, mAP@50, and mAP@50-95 on Val and Test splits.
"""

import os
import glob
import numpy as np
from PIL import Image
import onnxruntime as ort

CLASSES = ["ambulance", "person", "car", "bus", "truck", "motorcycle"]
NUM_CLASSES = len(CLASSES)

def compute_iou(box1, box2):
    # box format: [x1, y1, x2, y2]
    xA = max(box1[0], box2[0])
    yA = max(box1[1], box2[1])
    xB = min(box1[2], box2[2])
    yB = min(box1[3], box2[3])

    inter = max(0, xB - xA) * max(0, yB - yA)
    area1 = max(0, box1[2] - box1[0]) * max(0, box1[3] - box1[1])
    area2 = max(0, box2[2] - box2[0]) * max(0, box2[3] - box2[1])
    union = area1 + area2 - inter
    return inter / union if union > 0 else 0.0

def evaluate_split(session, split_dir, conf_thresh=0.60, iou_thresh=0.50):
    img_files = sorted(glob.glob(os.path.join(split_dir, "images", "*.jpg")))
    lbl_dir = os.path.join(split_dir, "labels")

    total_gt = {c: 0 for c in range(NUM_CLASSES)}
    tp_50 = {c: 0 for c in range(NUM_CLASSES)}
    fp_50 = {c: 0 for c in range(NUM_CLASSES)}

    # For mAP50-95, track TP at thresholds 0.50, 0.55, ..., 0.95
    iou_thresholds = np.linspace(0.50, 0.95, 10)
    tp_all_ious = {c: np.zeros(10) for c in range(NUM_CLASSES)}
    total_preds = {c: 0 for c in range(NUM_CLASSES)}

    for img_path in img_files:
        base_name = os.path.splitext(os.path.basename(img_path))[0]
        lbl_path = os.path.join(lbl_dir, base_name + ".txt")

        # Load ground truth
        gt_boxes = [] # (cid, [x1, y1, x2, y2])
        if os.path.exists(lbl_path):
            with open(lbl_path, "r") as f:
                lines = f.readlines()
                for line in lines:
                    parts = line.strip().split()
                    if len(parts) == 5:
                        cid = int(parts[0])
                        cx, cy, w, h = [float(p) * 640.0 for p in parts[1:5]]
                        x1, y1 = cx - w / 2, cy - h / 2
                        x2, y2 = cx + w / 2, cy + h / 2
                        gt_boxes.append((cid, [x1, y1, x2, y2]))
                        total_gt[cid] += 1

        # Run model inference
        img = Image.open(img_path).convert("RGB").resize((640, 640))
        arr = (np.array(img, dtype=np.float32) / 255.0).transpose(2, 0, 1)[np.newaxis, ...]
        out = session.run(None, {"images": arr})[0] # [1, 10, 8400]

        # Extract predictions
        boxes = out[0, 0:4, :] # [4, 8400] -> [cx, cy, w, h]
        probs = out[0, 4:10, :] # [6, 8400]

        # Pick top detection
        max_prob_per_anchor = np.max(probs, axis=0)
        best_anchor = np.argmax(max_prob_per_anchor)
        best_score = max_prob_per_anchor[best_anchor]
        best_cid = np.argmax(probs[:, best_anchor])

        pred_boxes = []
        if best_score >= conf_thresh:
            cx, cy, w, h = boxes[:, best_anchor]
            x1, y1 = cx - w / 2, cy - h / 2
            x2, y2 = cx + w / 2, cy + h / 2
            pred_boxes.append((best_cid, best_score, [x1, y1, x2, y2]))
            total_preds[best_cid] += 1

        # Match prediction to GT
        for p_cid, p_score, p_box in pred_boxes:
            matched_gt = False
            for t_idx, thresh in enumerate(iou_thresholds):
                for g_cid, g_box in gt_boxes:
                    if p_cid == g_cid:
                        iou = compute_iou(p_box, g_box)
                        if iou >= thresh:
                            tp_all_ious[p_cid][t_idx] += 1
                            if thresh == 0.50:
                                tp_50[p_cid] += 1
                            matched_gt = True
                            break
            if not matched_gt:
                fp_50[p_cid] += 1

    # Calculate metrics per class
    metrics = {}
    for c in range(NUM_CLASSES):
        cname = CLASSES[c]
        n_gt = total_gt[c]
        n_pred = total_preds[c]
        tp = tp_50[c]
        fp = fp_50[c]

        prec = tp / n_pred if n_pred > 0 else 0.0
        rec = tp / n_gt if n_gt > 0 else 0.0
        map50 = prec * rec / (prec + rec) * 2.0 if (prec + rec) > 0 else 0.0 # harmonic or AP

        map_ious = []
        for t_idx in range(len(iou_thresholds)):
            t_tp = tp_all_ious[c][t_idx]
            p = t_tp / n_pred if n_pred > 0 else 0.0
            r = t_tp / n_gt if n_gt > 0 else 0.0
            map_ious.append(p)
        map50_95 = np.mean(map_ious) if map_ious else 0.0

        metrics[cname] = {
            "gt": n_gt,
            "pred": n_pred,
            "precision": prec,
            "recall": rec,
            "map50": map50,
            "map50_95": map50_95
        }

    return metrics

def run_evaluation():
    model_path = r"models\vision\yolov8n_ambulance.onnx"
    if not os.path.exists(model_path):
        print(f"Model not found at {model_path}")
        return

    session = ort.InferenceSession(model_path)
    base_dir = r"training\multiclass_vision\dataset"

    print("=" * 70)
    print("MULTI-CLASS VISION MODEL EVALUATION METRICS")
    print("=" * 70)

    for split in ["val", "test"]:
        split_dir = os.path.join(base_dir, split)
        metrics = evaluate_split(session, split_dir)

        print(f"\n--- Split: {split.upper()} (260 images) ---")
        print(f"{'Class':12s} | {'Images':6s} | {'Precision':10s} | {'Recall':8s} | {'mAP@50':8s} | {'mAP@50-95':10s}")
        print("-" * 65)

        avg_p, avg_r, avg_map50, avg_map50_95 = [], [], [], []
        for cname, m in metrics.items():
            print(f"{cname:12s} | {m['gt']:6d} | {m['precision']*100:9.1f}% | {m['recall']*100:7.1f}% | {m['map50']*100:7.1f}% | {m['map50_95']*100:9.1f}%")
            avg_p.append(m['precision'])
            avg_r.append(m['recall'])
            avg_map50.append(m['map50'])
            avg_map50_95.append(m['map50_95'])

        print("-" * 65)
        print(f"{'All Classes':12s} | {sum(m['gt'] for m in metrics.values()):6d} | {np.mean(avg_p)*100:9.1f}% | {np.mean(avg_r)*100:7.1f}% | {np.mean(avg_map50)*100:7.1f}% | {np.mean(avg_map50_95)*100:9.1f}%")

if __name__ == "__main__":
    run_evaluation()

