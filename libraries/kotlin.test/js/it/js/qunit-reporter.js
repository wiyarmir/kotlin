QUnit.moduleStart = function (name, testEnvironment) {
	console.log("##teamcity[testSuiteStarted name='" + name + "']");
};

QUnit.moduleDone = function (name, failures, total) {
	console.log("##teamcity[testSuiteFinished name='" + name + "']");
};

QUnit.testStart = function (name, testEnvironment) {
	console.log("##teamcity[testStarted name='" + name + "']");
};

QUnit.testDone = function (name, failures, total) {
	if (failures > 0) {
		console.log("##teamcity[testFailed name='" + name + "'"
				 + " message='Assertions failed: " + failures + "'"
				 + " details='Assertions failed: " + failures + "']");
	}
	console.log("##teamcity[testFinished name='" + name + "']");
};
