# sensor-hub_rest-service

Rest API service for https://github.com/mvenditto/sensor-hub. 

## Build
By default needs to be packaged in JAR named 'rest-server'. 
If another name is needed, change it renaming resource/META-INF/spi.service.Service/{rest-service} file.

## Configuration (server.conf)

key | default | description
----|---------|-------------
port| 8081 | server port
context| / | http://{hostname:port}/{**context**}/...

## REST API overview
**NB** All urls are prefixed with: *http://{hostname:port}/{context}*

url | method | action
----|--------|-------------
/services | GET | all *services* running on this node
/drivers | GET | all available *drivers* on this node
/devices | GET | all *devices* instanced on this node
/devices/tasks | GET | get a map of all *tasks* for each device
/devices/:id/tasks | GET | get all *tasks* supported by a specific device
/devices/:id | DELETE | delete *device* with specified 'id'
/devices/:id/tasks/:task | PUT | execute a *task* for the specified device
/devices | POST | create a new *device* with a specified *driver*
/dataStreams | GET | all *datastreams*
/dataStreams/:id | GET | an *observation* from this datastream (*{datastream.sensorId}_{datastream.name}*)
/observedProperties | GET | all *observed properties*

#### Websockets
**NB** All urls are prefixed with: ***ws|wss**://{hostname:port}/{context}*

url | action
--- | ------
/dataStreams/:id | a stream of *observation* from this *datastream*, ':id' is the 'datastream id', generally  (*{datastream.sensorId}_{datastream.name}*)

### Responses

#### GET /services
```json
[
  	{ 
    		"name":"rest-server",
    		"version":"0.0.1",
   		"description":"service description",
    		"rootDir":"..\\ext\\services\\rest-server"
   	}
]
```

#### GET /drivers
```json
[
	{
    		"name": "driver 1",
    		"version": "0.0.1",
    		"description": "driver description",
    		"descriptorClassName": "driver.DriverDescriptor1"
   	}
]
```

#### GET /devices
```json 
[
	{
    		"id": 0,
    		"name": "device0",
    		"description": "device description",
    		"metadataEncoding": "application/pdf",
    		"metadata": "www.example.org/schema.pdf",
    		"driverName": "driver 1"
   	}
]
```

#### GET /devices/tasks
```json
[
	{
		"deviceId": 0,
		"supportedTasks": [
			{
				"type": "object",
				"properties":
				{
					"message": 
					{
						"type": "string",
						"minLength": 1
					}
				},
				"id": "#taskid",
				"additionalProperties": false,
				"title": "demo-task",
				"required": 
				[
					"message"
				],
				"description": "a demo task"
			}
		]
	}
]
```

#### GET /devices/:id/tasks
```json
[
	{
		"type": "object",
		"properties": 
		{
			"message": {
				"type": "string",
				"minLength": 1
			}
		},
		"id": "#demotask",
		"additionalProperties": false,
		"title": "demo-task",
		"required": 
		[
			"message"
		],
		"description": "a demo task"
	}
]
```

#### GET /dataStreams
```json
[
	{
    		"name": "temperature",
    		"description": "temperature datastream",
    		"observationType": "http://www.opengis.net/def/observationType/OGC-OM/2.0/OM_Measurement",
    		"observedProperty": 
     		{
       			"name": "temperature",
       			"definition": "http://mmisw.org/ont/cf/parameter/air_temperature",
       			"description": "temperature property"
     		},
    		"unitOfMeasurement":
    		{
       			"name": "DegreesCelsius",
       			"definition": "http://download.hl7.de/documents/ucum/ucumdata.html",
       			"symbol": "Cel"
    		},
    		"sensorId": 0
  	}
]
```

#### GET /observedProperties
```json
[
	{
    		"name": "temperature",
    		"definition": "http://mmisw.org/ont/cf/parameter/air_temperature",
    		"description":"temperature property"
   	}
]
```
#### GET /dataStreams/{dataStreamId}
```json
{
	"phenomenonTime": 
	{
		"secondsEpoch": 1525439104
	},
	"resultTime": 
	{
		"secondsEpoch": 1525439104
	},
	"result": "42",
	"featureOfInterest": 
	{
		"name": "helmet",
		"description": "fireman helmet",
		"encodingType": 
		{
			"name": "application/vnd.geo+json"
		},
		"feature": { "TODO" }
	},
	"parentDataStream": "{dataStreamId}"
}
```

#### POST *{new-device}* /devices
```json
{
	"name": "device1",
	"description": "description",
	"metadataEncoding": "application/pdf",
	"metadata" : "www.example.com/schema.pdf",
	"driverName": "driver 1"
}
```

#### PUT {task-schema} /devices/:id/tasks/:task
json input validated against *task* json schema
