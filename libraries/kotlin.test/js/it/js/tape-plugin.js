var tape = require('tape');
var kotlin_test = require('kotlin-test');

kotlin_test.setAdapter({
    suite: function (name, fn) {
        fn();
    },

    test: function (name, fn) {
        tape(name, function(t) {
            kotlin_test.setOkFun(function(actual, message) {
                t.ok(actual, message);
            });
            fn();
            t.end();
        });
    },

    ignore: function (name, fn) {
        tape.skip(name, function(t) {
            kotlin_test.setOkFun(function(actual, message) {
                t.ok(actual, message);
            });
            fn();
            t.end();
        });
    }
});
