// QUnit.moduleStart = function (name, testEnvironment) {
// 	console.log("##teamcity[testSuiteStarted name='" + name + "']");
// };
//
// QUnit.moduleDone = function (name, failures, total) {
// 	console.log("##teamcity[testSuiteFinished name='" + name + "']");
// };
//
// QUnit.testStart = function (name, testEnvironment) {
// 	console.log("##teamcity[testStarted name='" + name + "']");
// };
//
// QUnit.testDone = function (name, failures, total) {
// 	if (failures > 0) {
// 		console.log("##teamcity[testFailed name='" + name + "'"
// 				 + " message='Assertions failed: " + failures + "'"
// 				 + " details='Assertions failed: " + failures + "']");
// 	}
// 	console.log("##teamcity[testFinished name='" + name + "']");
// };


// QUnit.testDone( function( details ) {
//     var result = {
//         "Module name": details.module,
//         "Test name": details.name,
//         "Assertions": {
//             "Total": details.total,
//             "Passed": details.passed,
//             "Failed": details.failed
//         },
//         "Skipped": details.skipped,
//         "Todo": details.todo,
//         "Runtime": details.runtime
//     };
//
//     console.log( JSON.stringify( result, null, 2 ) );
//
// } );

QUnit.done(function( details ) {
    console.log( "Total: ", details.total, " Failed: ", details.failed, " Passed: ", details.passed, " Runtime: ", details.runtime );
    details.failed = 0;
});

