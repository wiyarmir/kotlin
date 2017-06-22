var tape = require('tape');
var kotlin_test = require('kotlin-test');

kotlin_test.setAdapter({
    suite: function (name, fn) {
        fn();
    },

    xsuite: function (name, fn) {
        // Do nothing
    },

    fsuite: function (name, fn) {
        fn();
    },

    test: function (name, fn) {
        tape(name, function (t) {
            kotlin_test.setAssertHook(function (result, expected, actual, lazyMessage) {
                t.ok(result, lazyMessage());
            });
            fn();
            t.end();
        });
    },

    xtest: function (name, fn) {
        tape.skip(name, function (t) {
            kotlin_test.setAssertHook(function (result, expected, actual, lazyMessage) {
                t.ok(result, lazyMessage());
            });
            fn();
            t.end();
        });
    },

    ftest: function (name, fn) {
        tape(name, function (t) {
            kotlin_test.setAssertHook(function (result, expected, actual, lazyMessage) {
                t.ok(result, lazyMessage());
            });
            fn();
            t.end();
        });
    }
});
