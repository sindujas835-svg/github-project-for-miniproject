import onnxruntime as ort
import numpy as np
from PIL import Image

def test():
    sess = ort.InferenceSession('models/vision/yolov8n_ambulance.onnx')
    CLASSES = ['ambulance', 'person', 'car', 'bus', 'truck', 'motorcycle']

    files = [
        'ambulance_positive_01.jpg',
        'person_negative_01.jpg',
        'civilian_car_negative_01.jpg',
        'bus_negative_01.jpg',
        'truck_negative_01.jpg',
        'motorcycle_negative_01.jpg'
    ]

    for fname in files:
        img = Image.open('sample_test_files/' + fname).convert('RGB').resize((640, 640))
        arr = (np.array(img, dtype=np.float32) / 255.0).transpose(2, 0, 1)[np.newaxis, ...]
        out = sess.run(None, {'images': arr})[0]
        probs = out[0, 4:10, :]
        max_p = probs.max(axis=1)
        best_cls = np.argmax(max_p)
        print(f"{fname:28s} -> Best: {CLASSES[best_cls]} ({max_p[best_cls]*100:.1f}%), Amb: {max_p[0]*100:.1f}%")

if __name__ == '__main__':
    test()

