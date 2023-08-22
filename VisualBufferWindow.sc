VisualBufferWindow {
  var b, m, <w;
  var data;
  var xFactor = 1;
  var refreshView = true; // this is to stop refreshing view while mouse is pressed
  var rangeMin, rangeMax;
  var updateTimeout = false;
  var font, boldFont, smallFont, smallerFont;
  var sampleRateText, sizeText, sizeBox, yFromBox, yToBox, xFromBox, xToBox, editableCheck, drawLinesCheck, alwaysOnTopCheck, saveContentsCheck, menuBox, menu;
  var refreshFunc;

  *new { |visualBuffer, bounds|
    ^super.newCopyArgs(visualBuffer).init(bounds);
  }

  pointsPerPixel { |size, pixels = 1| ^size * pixels / m.bounds.width }
  dataPointsPerPixel { |pixels = 1| ^data.size * pixels / m.bounds.width }

  updateFromView {
    var tmp;
    if (rangeMin.notNil and: (rangeMin <= rangeMax)) {
      // reverse engineer data squishing
      var insertArr = m.value[rangeMin..rangeMax].stutter(xFactor).collect(_.range(b.yMin, b.yMax)).as(FloatArray);
      if (b[rangeMin * xFactor .. (rangeMax + 1) * xFactor - 1] != insertArr) {
        b.put(rangeMin * xFactor, insertArr, false); // don't write cache until mouse up, below
      };
    };
    tmp = rangeMin;
    rangeMin = rangeMax;
    rangeMax = tmp;
  }

  init { |bounds|
    if (bounds != b.windowBounds) {
      b.windowBounds = bounds
    };
    refreshFunc = {
      if (refreshView) {
        defer {
          if (b.size > 0) { //  TODO: why do we need this? sometimes buffer gets caught mid refresh or something...
            m.bounds = w.bounds.copy.origin_(0@20).height_(w.bounds.height - if (b.menuVisible) { 60 } { 20 });
            menu.visible = b.menuVisible;
            sizeBox.visible = b.menuVisible;
            sizeText.visible = b.menuVisible;
            sampleRateText.visible = b.menuVisible;

            data = b.data;
            xFactor = 1;
            while { this.pointsPerPixel((b.xMax - b.xMin) / xFactor/*data.size/xFactor*/) > 50 } {
              xFactor = xFactor * 2;
            };
            data = data[0, xFactor .. (data.size - xFactor)];
            m.indexThumbSize_(m.bounds.width)
            .valueThumbSize_((m.bounds.height).linlin(100, 400, 2, 3))
            .editable_(b.editable)
            .drawLines_(b.drawLines)
            .value_(data.collect { |n| n.linlin(b.yMin, b.yMax, 0, 1, nil) })
            .reference_(0.linlin(b.yMin, b.yMax, 0, 1, nil) ! data.size)
            .startIndex_(b.xMin / xFactor)
            .size_(b.xMax / xFactor);
            sizeBox.value = b.size;
            xToBox.clipLo_(b.xMin + 1).clipHi_(b.size).value_(b.xMax);
            xFromBox.clipLo_(0).clipHi_(b.xMax - 1).value_(b.xMin);
            yFromBox.clipHi_(b.yMax).value_(b.yMin);
            yToBox.clipLo_(b.yMin).value_(b.yMax);
            sampleRateText.string = if (b.sampleRate.notNil) { "sr: " ++ b.sampleRate.asString } { "" };
            alwaysOnTopCheck.value = b.alwaysOnTop;
            saveContentsCheck.value = b.saveContents;
            editableCheck.value = b.editable;
            drawLinesCheck.value = b.drawLines;
            menuBox.value = b.menuVisible;
          };
        };
      };
    };

    font = Font("Courier", 16, false);
    boldFont = Font("Courier", 16, true);
    smallFont = font.copy.size_(13);
    smallerFont = smallFont.copy.size_(11);

    w = Window(b.name.asString ++ "  (~/" ++ PathName(b.pathSymbol.asString).asRelativePath("~".standardizePath) ++ ")", bounds).front.alwaysOnTop_(b.alwaysOnTop);

    // -------- menu top ---------
    w.view.background_(Color.white).resize_(5).setContextMenuActions(
      MenuAction("Normalize", { b.normalize }),
      MenuAction("Play", { b.play }),
      MenuAction("--------------"),
      MenuAction("Zoom to fit", { b.xMin = 0; b.xMax = b.size; b.yMin = b.minItem.floor; b.yMax = b.maxItem.ceil; }),
      MenuAction("--------------"),
      MenuAction("Free", { b.free }),
      MenuAction("Delete", { b.delete })
    );
    StaticText(w, Rect(10, 4, 2000, 16)).string_(b.name.asString).font_(boldFont);
    sampleRateText = StaticText(w, Rect(w.bounds.width - 124, 24, 200, 16)).font_(smallFont).resize_(3);
    sizeText = StaticText(w, Rect(w.bounds.width - 138, 3, 40, 16)).string_("size:").font_(smallFont).resize_(3);
    sizeBox = TextField(w, Rect(w.bounds.width - 95, 2, 68, 16)).font_(smallFont).string_(b.size.asString).resize_(3).action_({
      var intValue = sizeBox.string.asInteger;
      if (intValue != b.size and: intValue.isPositive) {
        b.size_(sizeBox.value);
      };
    });

    menuBox = Button(w, Rect(w.bounds.width - 19, 2, 16, 16)).states_([["+", Color.black, Color.white], ["-", Color.black, Color.white]]).font_(boldFont).action_({
      b.menuVisible = menuBox.value.asBoolean;
    }).resize_(3);

    // ---- menu bottom -----
    {
      var boxFactory = { |bounds, action|
        NumberBox(menu, bounds).font_(smallFont).align_(\center).action_(action).scroll_step_(0.1);
      };

      var xBoxFactory = { |bounds, action|
        NumberBox(menu, bounds).font_(smallFont).align_(\center).action_(action).scroll_step_(1).clipLo_(0);
      };

      menu = View(w, Rect(0, w.bounds.height - 40, w.bounds.width, 40)).resize_(8);

      StaticText(menu, Rect(3, 2, 15, 16)).string_("Y:").font_(font);
      yFromBox = boxFactory.(Rect(23, 2, 55, 17), { |v| b.yMin = v.value });
      StaticText(menu, Rect(82, 2, 15, 16)).string_("to").font_(smallFont);
      yToBox = boxFactory.(Rect(103, 2, 55, 17), { |v| b.yMax = v.value });

      StaticText(menu, Rect(3, 21, 15, 16)).string_("X:").font_(font);
      xFromBox = xBoxFactory.(Rect(23, 21, 55, 17), { |v| b.xMin = v.value });
      StaticText(menu, Rect(82, 21, 15, 16)).string_("to").font_(smallFont);
      xToBox = xBoxFactory.(Rect(103, 21, 55, 17), { |v| b.xMax = v.value });

      StaticText(menu, Rect(menu.bounds.width - 205, 2, 95, 16)).align_(\right).string_("always on top").font_(smallerFont).resize_(9);
      alwaysOnTopCheck = CheckBox(menu, Rect(menu.bounds.width - 106, 2, 17, 17)).resize_(9).action_({ b.alwaysOnTop = alwaysOnTopCheck.value });
      StaticText(menu, Rect(menu.bounds.width - 205, 21, 95, 16)).align_(\right).string_("save contents").font_(smallerFont).resize_(9);
      saveContentsCheck = CheckBox(menu, Rect(menu.bounds.width - 106, 21, 17, 17)).resize_(9).action_({ b.saveContents = saveContentsCheck.value });
      StaticText(menu, Rect(menu.bounds.width - 68, 2, 55, 16)).string_("editable").font_(smallerFont).resize_(9);
      editableCheck = CheckBox(menu, Rect(menu.bounds.width - 86, 2, 17, 17)).resize_(9).action_({ b.editable = editableCheck.value });
      StaticText(menu, Rect(menu.bounds.width - 68, 21, 50, 16)).string_("lines").font_(smallerFont).resize_(9);
      drawLinesCheck = CheckBox(menu, Rect(menu.bounds.width - 86, 21, 17, 17)).resize_(9).action_({ b.drawLines = drawLinesCheck.value });
    }.value;

    m = MultiSliderView(w, w.bounds.copy.origin_(0@20).height_(w.bounds.height - 60))
    .elasticMode_(1).showIndex_(false).resize_(5).background_(Color.gray(1, 0))
    .receiveDragHandler_({ b.read(View.currentDrag) })
    .action_({ |q|
      var index = q.index;

      refreshView = false;

      if (rangeMin.isNil) {
        rangeMin = index;
        rangeMax = index;
      } {
        if (index < rangeMin) {
          rangeMin = index
        };
        if (index > rangeMax) {
          rangeMax = index
        };
      };
      if (updateTimeout.not) {
        this.updateFromView();
        updateTimeout = true;
        fork {
          (b.size / 1000000).max(0.001).wait; // this seems to work to avoid file errors
          updateTimeout = false;
        }
      };
    })
    .mouseMoveAction_({ |view, x, y|
      var doUpdate = false;
      if (x > view.bounds.width and: rangeMin.notNil) {
        rangeMax = view.value.size;
        doUpdate = true;
      };
      if (x < 0 and: rangeMin.notNil) {
        rangeMin = 0;
        doUpdate = true;
      };
      if (doUpdate and: updateTimeout.not) {
        this.updateFromView; // this will not write cache
        rangeMin = nil;
        rangeMax = nil;
      };
    })
    .mouseUpAction_({
      refreshView = true;
      this.updateFromView;
      b.prWriteCache; // write cache on mouse up
      rangeMin = nil;
      rangeMax = nil;
    })
    .onClose_({
      b.removeDependant(refreshFunc);
      b.window = nil
    });

    w.view.onMove_({
      b.windowBounds = w.bounds;
    })
    .onResize_({
      b.windowBounds = w.bounds;
    });

    refreshFunc.();
    b.addDependant(refreshFunc);
  }

  front {
    w.front;
  }

  close {
    w.close;
  }

  bounds {
    ^w.bounds;
  }

  alwaysOnTop {
    ^w.alwaysOnTop;
  }

  alwaysOnTop_ { |val|
    w.alwaysOnTop_(val);
  }
}