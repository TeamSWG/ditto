![license](https://img.shields.io/github/license/teamswg/ditto.svg)
![GitHub release](https://img.shields.io/github/release/teamswg/ditto.svg)
![Github commits (since latest release)](https://img.shields.io/github/commits-since/teamswg/ditto/latest.svg)
![JDK](https://img.shields.io/badge/JDK-10-blue.svg?longCache=true&style=flat)
![discord](https://img.shields.io/discord/465088685197623296.svg)

# Introduction #

This is a Star Wars Galaxies Combat Upgrade server emulator for the Java Virtual Machine.
The vision for this software is:

* Providing an experience that's reasonably close to the original game servers
* Easily expandable with new functionality
* Good amount of configuration options for in-game features
* Efficient use of system resources and solid performance

The way we perform code reviews should reflect these points.

# Setting up a development environment #

Ready to help bring back an awesome MMORPG with your programming skills?

The following assumes that you're familiar with:

* Installing applications on your machine
* Command line interfaces
* VCSs, Git in particular
* Programming in general

## Java Development Kit ##

In order to compile the source code, you need a JDK installation on your machine. The `JAVA_HOME` environment variable
should point to the directory of the JDK! It should be version 10 as minimum. You can see your installed Java version
by running `java -version`.

## Docker ##
If you want to host a Combat Upgrade game server, you'll want to use our Docker image. You'll need to install Docker
on the machine you'll be using. A container using the image can then be run like so:
`docker run -i -t --network="host" teamswg/latest`

For developers, Docker images can be built of the code: `docker build -t teamswg/ditto:v0.1.0`

## Submodules ##

The project uses submodules. Get them by running: `git submodule update --init`

## Database ##

User information is read from a MongoDB database that can be run on any machine on your network. Default is localhost,
meaning same machine as you're running the game server on!

1. Create database: `use ditto`
2. Create a database user for Ditto: `db.createUser({user: "ditto", pwd: "pass", roles: []})`
3. Insert your game user into the users collection of your database: `db.users.insert({username: "user", password: "pass", accessLevel: "dev", banned: false, characters: []})`

## Gradle ##

This project uses Gradle as its build tool of choice.

Compile and run Holocores unit tests using Gradle: `./gradlew test --info`
Compile and run Holocores main code using Gradle: `./gradlew run`

## Forwarder ##

Ditto uses TCP for network communications, whereas SWG was programmed for UDP.  This adds numerous efficiencies with
long distance communications, but requires that a little more work is done on the client side.  If you are using the
launcher, you do not have to worry about this.  If you are not using the launcher, follow the guide
[here](https://bitbucket.org/projectswg/forwarder).
