# telegram-analyzer
Source Code for the Telegram Analyzer

---

#### API Setup

Go to [Telegram](http://my.telegram.org) and log in.
Click on API development tools and fill the required fields.


copy `api_id` & `api_hash` after clicking create app, this is required by the backend.


#### Preliminaries
Preliminaries for proper execution include a Linux system with [Docker](https://www.docker.com/) for the backend container running the database and the elastic search indexer, [GraalVM](https://www.graalvm.org/) as JVM for the backend and [NPM](https://www.npmjs.com/) to pack and serve the frontend.
Database and other configurations are handled by the configuration file under `src/main/resources/application.properties`.
This also holds the required passwords for the database and telegram API IDs, so handle them with care.

Before the initial start, your telegram api key, hash and the place for the session directory has to be configured:
```shell
com.mkleimann.telegram.directory=
com.mkleimann.telegram.api.id=
com.mkleimann.telegram.api.hash=
```
	
#### Backend
The backend relies on the database to start, therefore the docker containers have to boot first.
To orchestrate the containers, a docker-compose is served and can be started with `docker-compose up -d` in the docker folder.
Then the backend can be started by the Gradle build daemon. To execute, run `./gradlew quarkusDev`.

It is also possible to generate a native executable and a bundled docker image for running on a dedicated server.
Please consider reading more about [Quarkus/Native images](https://quarkus.io/guides/building-native-image) if interested.
	
#### Frontend
The frontend is packed and served by NPM including all web-pack components, by calling `npm start`. It is also possible to build a static artifact with `npm run build`. This requires a dedicated host for serving the frontend, which was outside the scope of this project. But for a proper use, this is necessary, due to the high compute power needed to host all components. Currently a somewhat powerful pc is needed to host the backend and frontend and also using the tool.
