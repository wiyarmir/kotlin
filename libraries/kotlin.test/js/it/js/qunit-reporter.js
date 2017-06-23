var Tester = require('./expectedTests');

var tester = new Tester();

QUnit.testDone(function(details) {

    // var result = {
    //     "Module name": details.module,
    //     "Test name": details.name,
    //     "Assertions": {
    //         "Total": details.total,
    //         "Passed": details.passed,
    //         "Failed": details.failed
    //     },
    //     "Skipped": details.skipped,
    //     "Todo": details.todo,
    //     "Runtime": details.runtime
    // };
    //
    // console.log( JSON.stringify( result, null, 2 ) );

    var testName = details.module + ' ' + details.name;
    if (details.skipped) {
        tester.pending(testName);
    }
    else if (!details.failed) {
        tester.passed(testName);
    }
    else {
        tester.failed(testName);
    }

} );

QUnit.done(function( details ) {
    // tester.end();
    details.failed = tester.failures;
});

