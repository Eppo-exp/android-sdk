# Make settings - @see https://tech.davis-hansson.com/p/make/
SHELL := bash
.ONESHELL:
.SHELLFLAGS := -eu -o pipefail -c
.DELETE_ON_ERROR:
MAKEFLAGS += --warn-undefined-variables
MAKEFLAGS += --no-builtin-rules

# Log levels
DEBUG := $(shell printf "\e[2D\e[35m")
INFO  := $(shell printf "\e[2D\e[36m🔵 ")
OK    := $(shell printf "\e[2D\e[32m🟢 ")
WARN  := $(shell printf "\e[2D\e[33m🟡 ")
ERROR := $(shell printf "\e[2D\e[31m🔴 ")
END   := $(shell printf "\e[0m")


.PHONY: default
default: help

## help - Print help message.
.PHONY: help
help: Makefile
	@echo "usage: make <target>"
	@sed -n 's/^##//p' $<

## test-data
testDataDir := eppo/src/androidTest/assets/
tempDir := ${testDataDir}/temp
gitDataDir := ${tempDir}/sdk-test-data
branchName := main
githubRepoLink := https://github.com/Eppo-exp/sdk-test-data.git
.PHONY: test-data
test-data: 
	rm -rf $(testDataDir)
	mkdir -p $(tempDir)
	cd ${tempDir} \
	    && git clone -b ${branchName} --depth 1 --single-branch ${githubRepoLink} \
	    && rm -rf RepoName/.git/
	cp ${gitDataDir}/rac-experiments-v3.json ${testDataDir}
	cp ${gitDataDir}/rac-experiments-v3-hashed-keys.json ${testDataDir}
	cp -r ${gitDataDir}/assignment-v2 ${testDataDir}
	rm -rf ${tempDir}

## test
.PHONY: test
test: test-data
	# $(INFO)Uninstalling old version of test app(END)
	adb uninstall cloud.eppo.android.test
	# $(INFO)Running tests(END)
	./gradlew runEppoTests

## test
.PHONY: test-github-wf
test-github-wf: test-data
	# $(INFO)Running tests(END)
	echo "MAVEN_USERNAME=test" >> ./gradle.properties
	echo "MAVEN_PASSWORD=test" >> ./gradle.properties
	./gradlew runEppoTests

.PHONY: publish-release
publish-release: test
		# $(INFO)Checking required gradle configuration(END)
		@for required_property in "MAVEN_USERNAME" "MAVEN_PASSWORD"; do \
				cat ~/.gradle/gradle.properties | grep -q $$required_property; \
				if [ $$? != 0 ]; then \
						echo "$(ERROR)ERROR: ~/.gradle/gradle.properties file is missing property: $$required_property$(END)"; \
						exit 1; \
				fi; \
		done

		# $(INFO)Publishing release(END)
		./gradlew :eppo:publishReleasePublicationToMavenRepository


