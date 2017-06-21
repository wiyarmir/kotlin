var mocha = require('mocha');
module.exports = MyReporter;

function MyReporter(runner) {
  mocha.reporters.Base.call(this, runner);
  var passes = 0;
  var failures = 0;

  var testMap = {
     "TestTest emptyTest": "ignored",
     "SimpleTest testFoo": "fail",
     "SimpleTest testBar": "pass",
     "SimpleTest testFooWrong": "ignored"
  };

  var check = function(title, result) {
    var expected = testMap[title];
    if (expected != result) { 
       console.log("Test '" + title + "' was expected to " + expected + "; actual result: " + result);
       failures++;
    }
    else {
      passes++;
    }
  };

  runner.on('pass', function(test) {
    check(test.fullTitle(), 'pass');
  });

  runner.on('fail', function(test, err) {
    check(test.fullTitle(), 'fail');
  });

  runner.on('pending', function(test) {
    check(test.fullTitle(), 'ignored');
  });

  runner.on('end', function(){
    console.log('end: %d/%d', passes, passes + failures);
    process.exit(failures);
  });
}
