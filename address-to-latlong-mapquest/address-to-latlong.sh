#!/bin/bash

KEY="7CJ6ZDE6GJQm6lhbv6dL3C0Q0XN9pRkf"
LOCATION=$@
curl --data-urlencode "location=$LOCATION" "http://www.mapquestapi.com/geocoding/v1/address?key=$KEY" | \
    jq --raw-output '[.results[0].locations[0].latLng.lat, .results[0].locations[0].latLng.lng] | join(",")'
