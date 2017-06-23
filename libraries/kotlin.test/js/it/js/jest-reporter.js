var Tester = require('./expectedTests');

var MyCustomReporter = function(globalConfig, options) {
    this._globalConfig = globalConfig;
    this._options = options;
    this._tester = new Tester();
};

MyCustomReporter.prototype.onRunComplete = function(contexts, results) {
    console.log('Custom reporter output:');
    console.log('GlobalConfig: ', this._globalConfig);
    console.log('Options: ', this._options);
    console.log('Contexts: ', contexts);
    console.log('Results: ', results);
    var testResults = results.testResults;
    for (var i = 0; i < testResults; i++) {
        var tr = testResults[i];

        if (tr.status == 'passed') {
            this._tester.passed(tr.name);
        }
        else if (tr.status == 'failed') {
            this._tester.failed(tr.name);
        }
        else {
            this._tester.pending(tr.name);
        }
    }

    this._tester.end();

    // results.numFailedTests = 0;
};

MyCustomReporter.prototype.getLastError = function() {
    return null;
};


module.exports = function(results) {
    var tester = new Tester();
    var testResults = results.testResults[0].testResults;
    console.log(testResults);
    try {
        for (var i = 0; i < testResults.length; i++) {
            var tr = testResults[i];


            if (tr.status == 'passed') {
                tester.passed(tr.fullName);
            }
            else if (tr.status == 'failed') {
                tester.failed(tr.fullName);
            }
            else {
                tester.pending(tr.fullName);
            }
        }

        console.log('Failed: ' + tester.failures + ' / ' + (tester.passes + tester.failures));

    } catch (e) {
        console.log(e);
    }
    tester.end();
};


// module.exports = JestProcessor;
//
// function JestProcessor(result) {
//     console.log('Result: ' + result);
// }