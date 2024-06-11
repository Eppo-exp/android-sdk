# Make settings - @see https://tech.davis-hansson.com/p/make/
# Reminder: Make relies on the tab preceding commands; if your IDE replaces tabs spaces it could mess this up

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
testDataDir := eppo/src/androidTest/assets
tempDir := ${testDataDir}/temp
gitDataDir := ${tempDir}/sdk-test-data
branchName := main
githubRepoLink := https://github.com/Eppo-exp/sdk-test-data.git
.PHONY: test-data
test-data: 
	rm -rf $(testDataDir)
	mkdir -p $(tempDir)
	git clone -b ${branchName} --depth 1 --single-branch ${githubRepoLink} ${gitDataDir}
	cp ${gitDataDir}/ufc/flags-v1.json ${testDataDir}
	cp ${gitDataDir}/ufc/flags-v1-obfuscated.json ${testDataDir}
	cp -r ${gitDataDir}/ufc/tests ${testDataDir}
	rm -rf ${tempDir}

## test
.PHONY: test
test: test-data
	# $(INFO)Uninstalling old version of test app(END)
	adb uninstall cloud.eppo.android.test
	# $(INFO)Running tests(END)
	./gradlew runEppoTests

check-maven-credentials-and-publish:
	# $(INFO)Checking required gradle configuration(END)
		@for required_property in "OSSRH_USERNAME" "OSSRH_PASSWORD"; do \
				cat ~/.gradle/gradle.properties | grep -q $$required_property; \
				if [ $$? != 0 ]; then \
						echo "$(ERROR)ERROR: ~/.gradle/gradle.properties file is missing property: $$required_property$(END)"; \
						exit 1; \
				fi; \
		done

		# $(INFO)Publishing release(END)
		./gradlew :eppo:publishReleasePublicationToMavenRepository

.PHONY: publish-release
publish-release: test check-maven-credentials-and-publish
