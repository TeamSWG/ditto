![License](https://img.shields.io/badge/license-GPLv3-blue.svg?longCache=true&style=flat)
![JDK](https://img.shields.io/badge/JDK-10-blue.svg?longCache=true&style=flat)
![Discord](https://img.shields.io/discord/373548910225915905.svg)
![Bitbucket open pull requests](https://img.shields.io/bitbucket/pr/projectswg/holocore.svg)

# Introduction #

This is a Star Wars Galaxies Combat Upgrade server emulator for the Java Virtual Machine.
The vision for this software is:

* Providing an experience that's reasonably close to the original game servers
* Easily expandable with new functionality
* Good amount of configuration options for in-game features
* *Highly* efficient use of system resources and solid performance

The way we perform code reviews should reflect these points.

You can find detailed information on the [wiki](https://bitbucket.org/projectswg/holocore/wiki/Home).

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

## Clientdata ##

This application reads a lot of information from the original game files. An installation of the game is therefore
required. Create a folder called `clientdata` in the root project directory. Extract the following folders of every
sku#_client.toc file to the `clientdata` folder:

* abstract
* appearance
* creation
* customization
* datatables
* footprint
* interiorlayout
* misc
* object
* quest
* snapshot
* string
* terrain

Note that every TOC file won't necessarily have all of these folders! If they're present, extract them.
A tool such as TRE Explorer is capable of opening the files and extracting their contents.

You should end up with a structure that looks something like this:
```
holocore/
	clientdata/
		abstract/
		appearance/
		creation/
		customization/
		datatables/
		footprint/
		...
	gradle/
	res/
	serverdata/
	src/
	.gitignore
	.gitmodules
	LICENSE.txt
	...
```

## Submodules ##

The project uses submodules. Get them by running: `git submodule update --init`

## Database ##

User information is read from a MongoDB database that must run on the same machine as this software.
1. Create database: `use ditto`
2. Create a user for Ditto: `db.createUser({user: "ditto", pwd: "pass", roles: []})`
3. Insert your user into the users collection of your database: `db.users.insert({username: "user", password: "pass", accessLevel: "dev", banned: false, characters: []})`

## Gradle ##

This project uses Gradle as its build tool of choice.

Compile and run Holocores unit tests using Gradle: `./gradlew test --info`
Compile and run Holocores main code using Gradle: `./gradlew run`

## Forwarder ##

Holocore uses TCP for network communications, whereas SWG was programmed for UDP.  This adds numerous efficiencies with
long distance communications, but requires that a little more work is done on the client side.  If you are using the
launcher, you do not have to worry about this.  If you are not using the launcher, follow the guide
[here](https://bitbucket.org/projectswg/forwarder).
