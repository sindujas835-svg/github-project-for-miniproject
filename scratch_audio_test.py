import os, soundfile as sf, numpy as np, onnxruntime as ort

sess = ort.InferenceSession('models/audio/siren_classifier.onnx')
for root, _, files in os.walk('training/siren/dataset/test'):
    for f in sorted(files):
        if f.endswith('.wav'):
            p = os.path.join(root, f)
            data, _ = sf.read(p, dtype='float32')
            out = sess.run(None, {'audio_input': data[:16000][None, ...]})[0]
            prob = 1.0 / (1.0 + np.exp(-out[0, 0]))
            print(f'{f}: logit={out[0,0]:.3f}, prob={prob:.3f}')

