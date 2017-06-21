var kotlin_test = require('kotlin-test');

kotlin_test.setAdapter({
    suite: function (name, fn) {
        describe(name, fn);
    },

    test: function (name, fn) {
        test(name, fn);
    },

    ignore: function (name, fn) {
        test.skip(name, fn);
    }
});
