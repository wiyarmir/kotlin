var MyCustomReporter = function(globalConfig, options) {
    this._globalConfig = globalConfig;
    this._options = options;
};

MyCustomReporter.prototype.onRunComplete = function(contexts, results) {
    console.log('Custom reporter output:');
    console.log('GlobalConfig: ', this._globalConfig);
    console.log('Options: ', this._options);
    console.log('Contexts: ', contexts);
    console.log('Results: ', results);
    // results.numFailedTests = 0;
};

MyCustomReporter.prototype.getLastError = function() {
    return null;
};


module.exports = MyCustomReporter;


// module.exports = JestProcessor;
//
// function JestProcessor(result) {
//     console.log('Result: ' + result);
// }