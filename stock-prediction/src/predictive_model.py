import torch

class PredictiveModel():
    def __init__(self, model, name, window, created_date, device="cpu"):
        self.id = f"{name}|{window}|{created_date}"
        self.model = model
        self.name = name
        self.window = window
        self.device = device

    def predict(self, X):
        # PyTorch Model
        if isinstance(self.model, torch.nn.Module):
            return self.model(torch.from_numpy(X).float().to(self.device)).detach().numpy()
        # Other callable model (TF?)
        elif callable(self.model):
            return self.model(X)
        # Scikit-learn
        else:
            try:
                return self.model.predict(X)
            except:
                pass
        raise "Invalid Model!"

    def __call__(self, X):
        return self.predict(X)
