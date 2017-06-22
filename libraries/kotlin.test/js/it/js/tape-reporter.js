var test = require('tape-catch');
var path = require('path');

process.on('exit', function() {
    console.log("On exit");
    process.exit(0);
});


var stream = test.createStream(/*{ objectMode: true }*/);

stream.on('data', function (row) {
    // console.log(JSON.stringify(row))
});

stream.on('skip', function(res) {
    console.log(JSON.stringify(res));
});

process.argv.slice(2).forEach(function (file) {
    require(path.resolve(file));
});
