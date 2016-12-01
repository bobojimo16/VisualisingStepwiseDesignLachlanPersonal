var testingMode = process.argv.slice(2).length > 0;
var fs = require('fs');
var ansi = require('ansi')
  , cursor = ansi(process.stdout)
var walk = function(dir, done) {
  var results = [];
  var list = fs.readdirSync(dir);
  var i = 0;
  (function next() {
    var file = list[i++];
    if (!file) return done(results);
    file = dir + '/' + file;
    var stat = fs.statSync(file);
    if (stat && stat.isDirectory()) {
      walk(file, function(res) {
        results = results.concat(res);
        next();
      });
    } else {
      results.push(file);
      next();
    }
  })();
};
const vm = require('vm');
var files = fs.readdirSync("app/scripts/");
files.forEach( function( file) {
  var stat = fs.statSync("app/scripts/"+file);
  if (stat && stat.isDirectory()) {
    walk("app/scripts/"+file, function(results) {
      results.forEach(function (file) {
        if (file.endsWith(".js") && !file.endsWith("app.js") && file.indexOf("tests") === -1) {
          include(file);
        }
      });
    });
  }
});
include("app/scripts/helper-functions.js");
include("app/scripts/index-iterator.es6.js");
include("app/scripts/constants.js");

if (!testingMode) {
  var express = require('express');
  var app = express();
  var http = require('http').Server(app);
  var io = require('socket.io')(http);
  var port = 5000;

  app.use(express.static('app'))
  app.use('/bower_components', express.static('bower_components'));
  io.on('connection', function (socket) {
    socket.emit('connectedToServer', {});
    socket.on('compile', function (obj, ack) {
      ack(Compiler.localCompile(obj.ast,obj.context));
    });
  });

  http.listen(port, function () {
    console.log('Server started on: *:' + port);
  });
} else {
  console.log("Entering testing mode");
  fs.writeFileSync("tests/results.txt","");
  walk("tests", function (results) {
    results.forEach(function (result) {
      if (result.endsWith("results.txt")) return;
      cursor.bold().yellow();
      console.log("Reading: " + result);
      cursor.reset();
      var code = fs.readFileSync(result, 'utf-8');

      var compile = Compiler.compile(code, {isLocal: true, isFairAbstraction: true});
      if (compile.message) {
        cursor.red();
        console.log("Error compiling, Message: "+compile.toString());
        if (compile.stack) {
          throw compile;
        }
        fs.appendFileSync("tests/results.txt",result+"  Error   "+compile.toString()+" \n");
      } else {
        cursor.green();
        console.log("Successfully compiled");
        fs.appendFileSync("tests/results.txt",result+"  Success   \n");

        var operations = compile.operations;
        if(operations.length !== 0){
          cursor.bold().yellow();
          console.log("Operations:");
          fs.appendFileSync("tests/results.txt", "Operations:\n");
          cursor.reset();

          var passed = 0;
          var failed = 0;
          for(var i = 0; i < operations.length; i++){
            var { operation, process1, process2, result } = operations[i];
            if(result){
              cursor.green();
              passed++;
            }
            else{
              cursor.red();
              failed++;
            }

            var op = process1 + ' ' + operation + ' ' + process2;
            fs.appendFileSync("tests/results.txt", op + ' = ' + result + '\n');
            console.log(op);
            cursor.reset();
          }

          cursor.bold().yellow();
          console.log("Results:");
          fs.appendFileSync("tests/results.txt", "Results:\n");
          cursor.reset();

          if(passed === operations.length){
            cursor.green();
            console.log("All operations passed!");
            fs.appendFileSync("tests/results.txt", "All operations passed!\n");
          }
          else{
            var outcome = failed + '/' + operations.length + ' operations failed';
            cursor.red();
            console.log(outcome);
            fs.appendFileSync("tests/results.txt", outcome + "\n");
          }
          cursor.reset();
        }
      }
    });
  });
}
function include(path) {
  var code = fs.readFileSync(path, 'utf-8');
  vm.runInThisContext(code, path);
}
function exitHandler(options, err) {
  cursor.red();
  if (err) console.log(err.stack);
  cursor.reset();
  if (options.exit) process.exit();
}

//do something when app is closing
process.on('exit', exitHandler.bind(null,{cleanup:true}));

//catches ctrl+c event
process.on('SIGINT', exitHandler.bind(null, {exit:true}));

//catches uncaught exceptions
process.on('uncaughtException', exitHandler.bind(null, {exit:true}));

