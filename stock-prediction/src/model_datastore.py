from src.model import Model

import pymongo
from pymongo import MongoClient


def mongo_client():
    """
    TODO: Add auth
    """
    return MongoClient()


def save_models(models: list[Model]):
    mongo = mongo_client()
    db = mongo.stock_analysis
    db.models.insert_many(map(lambda m: m.serialize(), models))
    mongo.close()


def get_model(model_id: str):
    mongo = mongo_client()
    db = mongo.stock_analysis
    obj = db.models.find_one({'id': {'$eq': model_id}})
    if obj is not None:
        ret = Model.deserialize(obj)
        mongo.close()
        return ret
    else:
        return None


def get_all_models() -> list[Model]:
    mongo = mongo_client()
    db = mongo.stock_analysis
    cur = db.models.find({})
    ret = [Model.deserialize(obj) for obj in cur]
    mongo.close()
    return ret


def update_model(model: Model) -> pymongo.results.UpdateResult:
    mongo = mongo_client()
    db = mongo.stock_analysis
    result = db.models.update_one({'id': {'$eq': model.model_id}}, model.serialize())
    mongo.close()
    return result