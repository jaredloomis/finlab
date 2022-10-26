import numpy as np
from sklearn.model_selection import train_test_split
import torch
from torch.utils.data import DataLoader, TensorDataset

from model import Model, SignalExpr
from signal_library import fetch_signal_set, FetchOptions


def get_train_test(feature_exprs: list[SignalExpr], label_exprs: list[SignalExpr], fetch_options: FetchOptions):
    sigs = fetch_signal_set(feature_exprs, label_exprs, fetch_options)

    Xs = []
    ys = []

    for ticker in fetch_options.symbols:
        X, y, Xy_date = sigs[ticker].to_xy()
        Xs.append(X)
        ys.append(y)

    X = np.concatenate(Xs, axis=0)
    y = np.concatenate(ys, axis=0)

    return train_test_split(X, y, test_size=0.05)


def round_batch_size(sample_count, approximately, leeway=None):
    """
    Round batch size to a more suitable value. This helps to avoid a
    problem where the final batch has a lot of samples, but not enough for
    a full batch, leading to many samples being thrown out.

    approximately: int, leeway: int
      decide on a chunk size around a number, with specified leeway
      (leeway defaults to `approximately // 10`).
    """
    if leeway is None:
        leeway = approximately // 10

    # Get the number of leftover samples if we use the suggested batch size
    best_leftover = sample_count - np.floor(sample_count / approximately) * approximately

    # Brute-force search for the value that yeilds the fewest leftovers
    # within the given leeway range.
    best_chunk_count = approximately
    for offset in range(-leeway, leeway):
        chunk_size = approximately + offset
        leftover = sample_count - np.floor(sample_count / chunk_size) * chunk_size
        if leftover < best_leftover:
            best_leftover = leftover
            best_chunk_count = chunk_size
    return best_chunk_count


def get_train_val_dataloaders(tickers, start, end):
    X_train, X_test, y_train, y_test = get_train_test(tickers, start, end)

    batch_size = round_batch_size(X_train.shape[0], 8096, leeway=200)

    # Convert X, y to torch tensors
    X_train_tensor = torch.from_numpy(X_train).float()
    X_test_tensor = torch.from_numpy(X_test).float()
    y_train_tensor = torch.from_numpy(y_train.reshape(y_train.shape[0], 1)).float()
    y_test_tensor = torch.from_numpy(y_test.reshape(y_test.shape[0], 1)).float()

    print(X_train_tensor.shape)
    print('Batch size:', batch_size)

    # Generators
    training_set = TensorDataset(X_train_tensor, y_train_tensor)
    dataloader_train = DataLoader(training_set, shuffle=True, batch_size=batch_size)

    validation_set = TensorDataset(X_test_tensor, y_test_tensor)
    dataloader_val = DataLoader(validation_set, shuffle=True, batch_size=batch_size)

    del X_train_tensor
    del X_test_tensor
    del y_train_tensor
    del y_test_tensor

    return dataloader_train, dataloader_val


# Training the model
def train(model: Model, criterion, optimizer, dataloader_train, dataloader_val, epochs=100, device='cuda'):
    for epoch in range(epochs):
        train_loss = 0.0

        # Training
        net.train()
        for local_batch, local_labels in dataloader_train:
            #if local_batch.shape[0] != batch_size:
            #    print(f"Wrong train batch size. Skipping batch.\nThrowing away {local_batch.shape[0]} samples.")
            #    continue
            local_batch, local_labels = local_batch.to(device), local_labels.to(device)

            # Forward pass: Compute predicted y by passing x to the model
            y_pred = net(local_batch)
            # Compute and print loss
            loss = criterion(y_pred, local_labels)
            # Zero gradients, perform a backward pass, update the weights.
            optimizer.zero_grad()
            loss.backward()
            optimizer.step()
            # Update loss
            train_loss += loss.item()

        # Validation
        net.eval()
        valid_loss = 0.0
        for data, labels in dataloader_val:
            #if data.shape[0] != batch_size:
            #    continue
            data, labels = data.to(device), labels.to(device)

            target = net(data)
            loss = criterion(target,labels)
            valid_loss += loss.item()

        print(f'Epoch {epoch+1} \t\t Training Loss: {train_loss / len(dataloader_train)} \t\t Validation Loss: {valid_loss / len(dataloader_val)}')
