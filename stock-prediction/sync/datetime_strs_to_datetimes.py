import pymongo

mongo = pymongo.MongoClient()
db = mongo.stock_analysis

# May need to delete index beforehand
try:
    db.candles_5min.update_many({'time': {'$type': 'string'}}, [{"$set": {"time": {"$toDate": "$time"}}}])
finally:
    mongo.close()
