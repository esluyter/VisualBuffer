VisualBuffer {
  /*
  caveats:
  - only built for single server use (classvar server)
  - buffer will be mono
  - buffer size defaults to 100
  - asynchronous command in methods:
       - refresh
       - read
       - normalize, updateInfo, gen, sine1 etc, cheby
  - buffers are saved globally by name - this should change to file-relative.

  questions:
  - is it always safe to e.g. buf.setn(whatever);buf.write(whatever) without a sync?
  */
  var <name, <buffer, <data;
  var <yMin = -1, <yMax = 1;
  var <xMin = 0, <xMax = 100;
  var <drawLines = false, <editable = true, <menuVisible = true;
  var <windowBounds, <>window;
  var <alwaysOnTop = false, <saveContents = true;
  var refreshRout;
  var <>action;
  var <cacheFolder, <pathSymbol;

  classvar <visualBuffers, <>server;

  *initClass {
    visualBuffers = ();
    server = Server.default;
    ServerQuit.add({ this.freeAll });
  }

  *new { |name, makeWindow = true|
    var vb;
    var cacheFolder, pathSymbol;

    if (thisProcess.nowExecutingPath.notNil) {
      var pathString = PathName(thisProcess.nowExecutingPath).pathOnly;
      pathSymbol = pathString.asSymbol;
      cacheFolder = pathString +/+ ".cacheVB";
    } {
      Error("You must save this .scd file somewhere!").throw;
    };

    if (server.serverRunning.not) {
      Error("Server is not running.\nRun Server.default.boot and try again.").throw;
      ^nil;
    };

    if (File.exists(cacheFolder).not) {
      File.mkdir(cacheFolder);
    };

    if (visualBuffers[pathSymbol].isNil) {
      visualBuffers[pathSymbol] = ();
    };

    if (visualBuffers[pathSymbol][name].notNil) {
      vb = visualBuffers[pathSymbol][name];
    } {
      vb = super.newCopyArgs(name).init(cacheFolder, pathSymbol);
    };

    if (makeWindow) {
      ^vb.makeWindow;
    } {
      ^vb;
    };
  }

  init { |argCacheFolder, argPathSymbol|
    cacheFolder = argCacheFolder;
    pathSymbol = argPathSymbol;

    if (this.prReadCache.not) {
      buffer = Buffer.alloc(server, xMax);
      data = FloatArray.newClear(xMax);
    };
    visualBuffers[pathSymbol][name] = this;
    this.addDependant({ this.action.value(this) });
  }

  free {
    buffer.free;
    if (window.notNil) {
      window.close;
    };
    visualBuffers[pathSymbol][name] = nil;
  }

  *freeAll {
    visualBuffers.do { |dict|
      dict.do(_.free);
    }
  }

  delete {
    this.prDeleteCache;
    this.free;
  }

  refresh { // asynchronous
    this.prWriteCache(false); // this can be here because it's telling the buffer to write a file
    buffer.loadToFloatArray(0, -1, { |arr|
      xMin = 0; // TODO: fix x range behavior
      xMax = arr.size;
      this.prData_(arr);
      this.prWriteFieldsCache;
    });
  }

  data_ { |arr|
    xMin = 0; // TODO: figure out x range behavior
    xMax = arr.size;
    arr = arr.as(FloatArray);
    this.prData_(arr);
    buffer.free;
    buffer = Buffer.loadCollection(server, data, 1, { this.prWriteCache; });
  }

  prData_ { |arr| // doesn't update buffer contents
    data = arr;
    this.changed(\data);
  }

  put { |index, arr, writeCache = true|
    arr = arr.asArray;
    arr.do { |item, i|
      data.put(index + i, item);
    };
    if (arr.size < 1634) {
      buffer.setn(index, arr);
    } {
      buffer.loadCollection(arr, index);
    };
    if (writeCache) { // TODO: better to just use loadCollection and add an action?
      //fork {  // seems like it works reliably without forking....?
      //  server.sync;
        this.prWriteCache;
      //};
    };
    this.changed(\data);
  }

  putSeries { |first, second, last, value|
    this.data_(data.putSeries(first, second, last, value));
  }

  size_ { |numFrames|
    xMin = 0;
    xMax = numFrames;
    numFrames = numFrames.asInteger;
    this.data_(data.extend(numFrames, 0.0));
  }

  read { |path, resize = true, channel = 0| // asynchronous; wait for data to be accurate
    if (File.exists(path)) {
      if (resize) {
        buffer.free;
        buffer = Buffer.readChannel(server, path, 0, if (resize) { -1 } { data.size }, [channel], { this.refresh });
      } {
        buffer.readChannel(path, 0, -1, channels: [channel], action: { this.refresh });
      };
    };
  }

  editable_ { |val|
    editable = val;
    this.prWriteFieldsCache;
    this.changed(\editable);
  }

  drawLines_ { |val|
    drawLines = val;
    this.prWriteFieldsCache;
    this.changed(\drawLines)
  }

  saveContents_ { |val|
    saveContents = val;
    this.prWriteCache;
    this.changed(\saveContents);
  }

  menuVisible_ { |val|
    menuVisible = val;
    this.prWriteFieldsCache;
    this.changed(\menuVisible);
  }

  yMin_ { |val|
    yMin = val;
    this.prWriteFieldsCache;
    this.changed(\yMin);
  }

  yMax_ { |val|
    yMax = val;
    this.prWriteFieldsCache;
    this.changed(\yMax);
  }

  xMin_ { |val|
    xMin = val;
    this.prWriteFieldsCache;
    this.changed(\xMin);
  }

  xMax_ { |val|
    xMax = val;
    this.prWriteFieldsCache;
    this.changed(\xMax);
  }

  windowBounds_ { |val|
    windowBounds = val;
    this.prWriteFieldsCache;
    this.changed(\windowBounds);
  }

  // ----- buffer operations : asynchronous ------

  prSyncRefresh {
    fork {
      server.sync;
      this.refresh;
    };
  }

  normalize { |newmax = 1|
    buffer.normalize(newmax);
    this.prSyncRefresh;
  }

  /*
  fill { |startAt = 0, numFrames, value ... more|
    numFrames = numFrames ?? buffer.numFrames;
    buffer.fill(startAt, numFrames, value, *more);
    this.prSyncRefresh;
  }
  */
  fill { |value| // like Array fill, not Buffer fill
    this.data_(data.fill(value));
  }

  // copyData

  play { |loop = false, mul = 1|
    ^buffer.play(loop, mul);
  }

  query { buffer.query }

  updateInfo { |action| buffer.updateInfo(action) }

  gen { |genCommand, genArgs, normalize = true, asWavetable = false, clearFirst = true|
    buffer.gen(genCommand, genArgs, normalize, asWavetable, clearFirst);
    this.prSyncRefresh;
  }

  sine1 { |amps, normalize = true, asWavetable = false, clearFirst = true|
    buffer.sine1(amps, normalize, asWavetable, clearFirst);
    this.prSyncRefresh;
  }

  sine2 { |freqs, amps, normalize = true, asWavetable = false, clearFirst = true|
    buffer.sine2(freqs, amps, normalize, asWavetable, clearFirst);
    this.prSyncRefresh;
  }

  sine3 { |freqs, amps, phases, normalize = true, asWavetable = false, clearFirst = true|
    buffer.sine3(freqs, amps, phases, normalize, asWavetable, clearFirst);
    this.prSyncRefresh;
  }

  cheby { |amps, normalize = true, asWavetable = false, clearFirst = true|
    buffer.cheby(amps, normalize, asWavetable, clearFirst);
    this.prSyncRefresh;
  }

  write { |path, headerFormat = "wav", sampleFormat = "float", numFrames = -1,
						startFrame = 0, leaveOpen = false, completionMessage|
    buffer.write(path, headerFormat, sampleFormat, numFrames, startFrame, leaveOpen, completionMessage);
  }


  // ------ cacheing -------

  cachePath { ^(cacheFolder +/+ name.asString ++ ".wav"); }
  fieldsCachePath { ^(this.cachePath ++ ".txt") }

  prWriteCache { |writeFields = true|
    if (saveContents) {
      buffer.write(this.cachePath, "wav", "float");
    } {
      if (File.exists(this.cachePath)) {
        File.delete(this.cachePath);
      };
    };
    if (writeFields) {
      this.prWriteFieldsCache;
    };
  }

  prWriteFieldsCache {
    defer {
      [yMin, yMax, xMin, xMax, drawLines, editable, menuVisible, windowBounds, alwaysOnTop, saveContents].writeArchive(this.fieldsCachePath);
    };
  }

  prReadCache {
    var path = this.cachePath;
    var exists = File.exists(path);
    var archivePath = this.cachePath ++ ".txt";
    if (exists) {
      var sf = SoundFile.openRead(path);
      buffer = Buffer.read(server, path);
      data = FloatArray.newClear(sf.numFrames);
      xMax = sf.numFrames;
      sf.readData(data);
      sf.close;
    };
    if (File.exists(archivePath)) {
      var archive = Object.readArchive(archivePath);
      # yMin, yMax, xMin, xMax, drawLines, editable, menuVisible, windowBounds, alwaysOnTop, saveContents = archive;
    };
    ^exists;
  }

  prDeleteCache {
    if (File.exists(this.cachePath)) {
      File.delete(this.cachePath);
    };
    if (File.exists(this.fieldsCachePath)) {
      File.delete(this.fieldsCachePath);
    };
  }

  // ------ information -------

  size { ^data.size; }

  bufnum { ^buffer.bufnum }
  numFrames { ^buffer.numFrames }
  numChannels { 1 }
  sampleRate { ^buffer.sampleRate; }
  path { ^buffer.path }
  duration { ^buffer.duration }
  asUGenInput { ^buffer.bufnum }

  at { |index| ^data.clipAt(index) } // mimics Pd expr array1[$i1] behavior
  clipAt { |index| ^data.clipAt(index) }
  wrapAt { |index| ^data.wrapAt(index) }
  copySeries { |first, second, last| ^data.copySeries(first, second, last) }

  maxItem { |function| ^data.maxItem(function) }
  minItem { |function| ^data.minItem(function) }

  // -------- GUI ----------

  makeWindow {
    fork {
      server.sync;
      defer { if (window.isNil) { window = VisualBufferWindow(this, windowBounds) } { window.front } };
    };
  }

  alwaysOnTop_ { |val| alwaysOnTop = val; window.alwaysOnTop_(val) }

  // -------- REFRESH ROUTINE ---------

  autoRefresh { |freq = 20|
    refreshRout.stop;
    if (freq.isNumber) {
      refreshRout = fork {
        loop {
          this.refresh;
          freq.reciprocal.wait;
        };
      };
    };
  }
}