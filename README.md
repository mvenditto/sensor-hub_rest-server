# sensor-hub_rest-service

Rest API service for https://github.com/mvenditto/sensor-hub. 

## Build
By default needs to be packaged in JAR named 'rest-server'. 
If another name is needed, change it renaming resource/META-INF/spi.service.Service/{rest-service} file.

## REST API overview
**NB** All urls are prefixed with: *http://{hostname:port}/{context}*

url | method | action
----|--------|-------------
/services | GET | all *services* running on this node
/drivers | GET | all available *drivers* on this node
/devices | GET | all *devices* instanced on this node
/devices/:id | DELETE | delete *device* with specified 'id'
/devices | POST | create a new *device* with a specified *driver*
/dataStreams | GET | all *datastreams*
/observedProperties | GET | all *observed properties*

#### Websockets
**NB** All urls are prefixed with: ***ws|wss**://{hostname:port}/{context}*

url | action
--- | ------
/dataStreams/:id | a stream of *observation* from this *datastream*, ':id' is the 'datastream id', generally *{datastream.sensor.id}_{datastream.name}*

### Responses
