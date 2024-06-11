// Language Server Protocol representation of a Document.
//
// EVENTUALLY: we need a class hierarchy that looks like:
//   Document {}
//   ScIDEDocument : Document {}
//   LSPDocument : Document {}
// `Document` becomes our common interface, with each subclass implementing
// specific details.
//
// @TODO Implement incremental edits to documents
// @TODO Sync allDocuments
// @TODO Fill in `title` field with something reasonable.
// @TODO Sync `isEdited`
// @TODO Properly handle documents without a path.
// @TODO Connect toFrontAction/endFrontAction for focus (this may not be valid/possible for clients!)
// @TODO Probably, key+mouse actions are only valid for ScIDEDocument, and do not make sense for LSP
// @TODO Sync selection
// @TODO Allow multi-select?
// @TODO Linked environments won't work for LSP - migrate to a model where code execution is linked to a Document, and wrap this execution in an Environment:use?
// @TODO `current` doesn't make sense for LSP? Move to ScIDEDocument and respond nil in base class?
// @TODO Allow LSPDocument to provide CodeLens's, so these can be specified in sclang for cool clickable inline actions?

LSPDocument : Document {    
    // Primary LSP properties
    var <>languageId, <version;
    
    // derived properties
    var string, <isOpen = false;
    var <envir, <savedEnvir;
    var <editable = true, <promptToSave = true;
    
    *initClass{
        asyncActions = IdentityDictionary.new;
        Document.implementingClass = LSPDocument;
    }
    
    *new {
        |quuid|
        ^super.new().quuid_(quuid)
    }
    
    *open { | path, selectionStart=0, selectionLength=0, envir |
        LSPConnection.connection.request('window/showDocument', (
            uri: "file://%".format(path.standardizePath),
            takeFocus: true,
            // selection: 
        ))
    }
    
    *syncFromIDE {|uri, string|
        var doc;
        doc = this.findByQUuid(uri);
        doc.initFromIDE(
            uri,
            uri,
            string,
            false,
            uri.copy.replace("file://", "").urlDecode,
            0,
            0
        );
        
        ^doc
            
            // var doc;
            // isEdited = isEdited.booleanValue;
            // chars = String.fill(chars.size, {|i| chars[i].asAscii});
            // title = String.fill(title.size, {|i| title[i].asAscii});
            // if(path.isArray) {
            // 	path = String.fill(path.size, {|i| path[i].asAscii});
            // } {
            // 	path = nil;
            // };
            // if((doc = this.findByQUuid(quuid)).isNil, {
            // 	doc = super.new.initFromIDE(quuid, title, chars, isEdited, path, selStart, selSize);
            // 	doc.prAdd;
            // }, {doc.initFromIDE(quuid, title, chars, isEdited, path, selStart, selSize)});
    }
    
    *syncDocs {|docInfo| // [quuid, title, string, isEdited, path, selStart, selSize]
        // docInfo.do({|info| this.syncFromIDE(*info) });
    }
    
    *executeAsyncResponse {|funcID ...args|
        // var func;
        // func = asyncActions[funcID];
        // asyncActions[funcID] = nil;
        // func.value(*args);
    }
    
    *findByQUuid {
        |quuid|
        var found = allDocuments.detect({|doc| doc.quuid == quuid });
        
        ^found ?? {
            found = LSPDocument(quuid);
            found.prAdd();
            found;
        }
    }
    
    *setActiveDocByQUuid {|quuid|
        // var newCurrent;
        // newCurrent = this.findByQUuid(quuid);
        // this.prCurrent_(newCurrent);
    }
    
    *prCurrent_ {|newCurrent|
        // current = this.current;
        // if((newCurrent === current).not, {
        // 	if(current.notNil, {current.didResignKey});
        // 	newCurrent.didBecomeKey;
        // });
    }
    
    *dir_ { | path |
        // path = path.standardizePath;
        // if(path == "") { dir = path } {
        // 	if(pathMatch(path).isEmpty) { ("there is no such path:" + path).postln } {
        // 		dir = path ++ "/"
        // 	}
        // }
    }
    
    *standardizePath { | p |
        // var pathName;
        // pathName = PathName(p.standardizePath);
        // ^if(pathName.isRelativePath) {
        // 	dir  ++ pathName.fullPath
        // } {
        // 	pathName.fullPath
        // }
    }
    
    *abrevPath { | path |
        // if(path.size < dir.size) { ^path };
        // if(path.copyRange(0,dir.size - 1) == dir) {
        // 	^path.copyRange(dir.size, path.size - 1)
        // };
        // ^path
    }
    
    *openDocuments {
        ^allDocuments
    }
    
    *hasEditedDocuments {
        allDocuments.do { | doc |
            if(doc.isEdited) {
                ^true;
            }
        }
        ^false
    }
    
    *closeAll {
        allDocuments.do { | doc | doc.close }
    }
    
    *closeAllUnedited {
        var listenerWindow;
        allDocuments.do({ | doc |
            if(doc.isEdited.not, {
                doc.close;
            });
        });
    }
    
    closed {
        onClose.value(this); // call user function
        this.restorePreviousEnvironment;
        allDocuments.remove(this);
    }
    
    front {
        // this.class.prCurrent_(this);
        // ScIDE.setCurrentDocumentByQUuid(quuid);
    }
    
    path {
        if (quuid.contains("file://")) {
            ^quuid.copy.replace("file://", "").urlDecode
        } {
            ^nil
        }
    }
    
    save {
        "LSPDocument:save is not yet implemented".warn;
    }
    
    initFromLSP {
        |inLanguageId, inVersion, inText|
        Log('LanguageServer.quark').info("Creating LSP document % [size=%]", quuid, inText.size);
        
        title = this.path !? { |p| PathName(p).fileNameWithoutExtension } ?? { "unknown" };
        isEdited = false;
        
        languageId = languageId;
        version = inVersion;
        string = inText;
        
        this.changed(\string, this.string);
    }
    
    initFromDisk {
        Log('LanguageServer.quark').info("Loading LSP document from disk % [size=%]", quuid);
        
        title = this.path !? { |p| PathName(p).fileNameWithoutExtension } ?? { "unknown" };
        isEdited = false;
        
        // languageId = "";
        version = 0;
        string = File.readAllString(this.path);
    }
    
    applyChange {
        |newVersion, change|
        var start, end;
        
        version = newVersion;
        
        if (change.isWholeDocument) {
            string = change.text
        } {
            #start, end = change.stringIndices(string);
            string = string.copyRange(0, start-1) ++ change.text ++ string.copyRange(end, string.size)
        };
        
        isEdited = true;
        
        this.changed(\string, this.string);
    }
    
    documentWasSaved {
        isEdited = false;
    }
    
    isOpen_{
        |open|
        if (isOpen != open) {
            isOpen = open;
            this.changed(\isOpen, isOpen);
        };
        if (isOpen.not) {
            this.closed();
        }
    }
    
    initFromIDE {|id, argtitle, argstring, argisEdited, argPath, selStart, selSize|
        Log('LanguageServer.quark').info("Syncing document % to size=%", id, argstring.size);
        
        quuid = id;
        title = argtitle;
        string = argstring;
        isEdited = argisEdited;
    }
    
    initFromPath { | argpath, selectionStart, selectionLength |
        // quuid = ScIDE.getQUuid;
        // this.prReadTextFromFile(argpath);
        // this.propen(argpath, selectionStart, selectionLength);
        // path = argpath;
        // title = path.basename;
        // isEdited = false;
        // this.prAdd;
    }
    
    textChanged {|index, numCharsRemoved, addedChars|
        // addedChars = String.fill(addedChars.size, {|i| addedChars[i].asAscii});
        // textChangedAction.value(this, index, numCharsRemoved, addedChars);
    }
    
    propen {|path, selectionStart, selectionLength, envir|
        // ^ScIDE.open(path, selectionStart, selectionLength, quuid)
    }
    
    close {
        // ScIDE.close(quuid);
    }

    // asynchronous get
    // range -1 means to the end of the Document
    // 'getText' tried to replace this approach,
    // but text mirroring is unstable in Windows.
    // so we need to keep a backup approach.
    getTextAsync { |action, start = 0, range -1|
        // var funcID;
        // funcID = ScIDE.getQUuid; // a unique id for this function
        // asyncActions[funcID] = action; // pass the text
        // ScIDE.getTextByQUuid(quuid, funcID, start, range);
    }
    
    getText { |start = 0, range -1|
        // ^this.prGetTextFromMirror(quuid, start, range);
    }
    
    prGetTextFromMirror {|id, start=0, range = -1|
        // _ScIDE_GetDocTextMirror
        // this.primitiveFailed
    }
    
    // asynchronous set
    prSetText {|text, action, start = 0, range -1|
        // var funcID;
        // // first set the back end mirror
        // this.prSetTextMirror(quuid, text, start, range);
        // // set the SCIDE Document
        // ScIDE.setTextByQUuid(quuid, funcID, text, start, range);
    }
    
    // set the backend mirror
    prSetTextMirror {|quuid, text, start, range|
        // _ScIDE_SetDocTextMirror
        // this.primitiveFailed
    }
    
    prSetSelectionMirror {|quuid, start, size|
        // _ScIDE_SetDocSelectionMirror
        // this.primitiveFailed
    }
    
    text_ {|newString|
        string = newString;
    }
    
    text {
        ^string
            // ^this.prGetTextFromMirror(quuid, 0, -1);
    }
    
    rangeText { | rangestart=0, rangesize=1 |
        // ^this.prGetTextFromMirror(quuid, rangestart, rangesize);
    }
    
    insertText {|string, index = 0|
        // this.prSetText(string, nil, index, 0);
    }
    
    getChar {|index = 0|
        // ^this.prGetTextFromMirror(quuid, index, 1);
    }
    
    setChar {|char, index = 0|
        // this.prSetText(char.asString, nil, index, 1);
    }
    
    == {
        // |that| ^(this.quuid === that.quuid);
    }
    
    hash {
        // ^quuid.hash
    }
    
    didBecomeKey {
        // this.class.current = this;
        // this.pushLinkedEnvironment;
        // toFrontAction.value(this);
    }
    
    didResignKey {
        // endFrontAction.value(this);
        // this.restorePreviousEnvironment;
    }
    
    keyDown { | modifiers, unicode, keycode, key |
        // var character = unicode.asAscii;
        // var cocoaModifiers = QKeyModifiers.toCocoa(modifiers);
        // this.class.globalKeyDownAction.value(this,character, cocoaModifiers, unicode, keycode);
        // keyDownAction.value(this,character, cocoaModifiers, unicode, keycode, key);
    }
    
    keyUp { | modifiers, unicode, keycode, key |
        // var character = unicode.asAscii;
        // var cocoaModifiers = QKeyModifiers.toCocoa(modifiers);
        // this.class.globalKeyUpAction.value(this,character, cocoaModifiers, unicode, keycode);
        // keyUpAction.value(this,character, cocoaModifiers, unicode, keycode, key);
    }
    
    mouseDown { | x, y, modifiers, buttonNumber, clickCount |
        // var cocoaModifiers = QKeyModifiers.toCocoa(modifiers);
        // mouseDownAction.value(this, x, y, cocoaModifiers, buttonNumber, clickCount)
    }
    
    mouseUp { | x, y, modifiers, buttonNumber |
        // var cocoaModifiers = QKeyModifiers.toCocoa(modifiers);
        // mouseUpAction.value(this, x, y, cocoaModifiers, buttonNumber)
    }
    
    keyDownAction_ {|action|
        // keyDownAction = action;
        // ScIDE.setDocumentKeyDownEnabled(quuid, action.notNil);
    }
    
    keyUpAction_ {|action|
        // keyUpAction = action;
        // ScIDE.setDocumentKeyUpEnabled(quuid, action.notNil);
    }
    
    mouseDownAction_ {|action|
        // mouseDownAction = action;
        // ScIDE.setDocumentMouseDownEnabled(quuid, action.notNil);
    }
    
    mouseUpAction_ {|action|
        // mouseUpAction = action;
        // ScIDE.setDocumentMouseUpEnabled(quuid, action.notNil);
    }
    
    textChangedAction_ {|action|
        // textChangedAction = action;
        // ScIDE.setDocumentTextChangedEnabled(quuid, action.notNil);
    }
    
    *globalKeyDownAction_ {|action|
        // globalKeyDownAction = action;
        // ScIDE.setDocumentGlobalKeyDownEnabled(action.notNil);
    }
    
    *globalKeyUpAction_ {|action|
        // globalKeyUpAction = action;
        // ScIDE.setDocumentGlobalKeyUpEnabled(action.notNil);
    }
    
    title_ {|newTitle|
        // title = newTitle;
        // ScIDE.setDocumentTitle(quuid, newTitle);
    }
    
    prSetEdited {|flag|
        // isEdited = flag.booleanValue;
    }
    
    // this initialises the lang side text mirror
    prReadTextFromFile {|path|
        // var file;
        // file = File.new(path, "r");
        // if (file.isOpen.not, {
        // 	error("Document open failed\n");
        // });
        // this.prSetTextMirror(quuid, file.readAllString, 0, -1);
        // file.close;
    }
    
    prAdd {
        allDocuments = allDocuments.add(this);
        // if (autoRun) {
        // 	if (this.rangeText(0,7) == "/*RUN*/")
        // 	{
        // 		this.text.interpret;
        // 	}
        // };
        initAction.value(this);
    }
    
    isFront {
        // ^Document.current === this
    }
    
    selectionStart {
        // ^this.prGetSelectionStart(quuid)
    }
    
    prGetSelectionStart {|id|
        // _ScIDE_GetDocSelectionStart
        // ^this.primitiveFailed;
    }
    
    selectionSize {
        // ^this.prGetSelectionRange(quuid)
    }
    
    prGetSelectionRange {|id|
        // _ScIDE_GetDocSelectionRange
        // ^this.primitiveFailed;
    }
    
    string { | rangestart, rangesize = 1 |
        if (string.isNil) {
            this.initFromDisk();
        };
        if(rangestart.isNil,{
            ^string;
        });
        ^this.rangeText(rangestart, rangesize);
    }
    
    string_ { | string, rangestart = -1, rangesize = 1 |
        // this.prSetText(string, nil, rangestart, rangesize);
    }
    
    selectedString {
        // ^this.rangeText(this.selectionStart, this.selectionSize);
    }
    
    selectedString_ { | txt |
        // this.prSetText(txt, nil, this.selectionStart, this.selectionSize);
    }
    
    currentLine {
        // ^this.getSelectedLines(this.selectionStart, 0);
    }
    
    getSelectedLines { | rangestart = -1, rangesize = 0 |
        // var start, end, str, max;
        // str = this.string;
        // max = str.size;
        // start = rangestart;
        // end = start + rangesize;
        // while {
        // 	str[start] !== Char.nl and: { start >= 0 }
        // } { start = start - 1 };
        // while {
        // 	str[end] !== Char.nl and: { end < max }
        // } { end = end + 1 };
        // ^str.copyRange(start + 1, end);
    }
    
    charRangeForLine {
        |line|
        var startChar = 0, currentLine = 0, endChar = 0;
        var string = this.text;
        var stringSize = string.size;
        
        while {
            (currentLine < line) and: { startChar < stringSize };
        } {
            if (string[startChar] == Char.nl) {
                currentLine = currentLine + 1;
            };
            startChar = startChar + 1;
        };
        endChar = startChar;
        
        while {
            (string[endChar + 1] != Char.nl) and: {
                endChar < string.size
            }
        } {
            endChar = endChar + 1
        };
        
        ^[startChar, endChar]
    }
    
    getLine {
        |line|
        var start, end;
        #start, end = this.charRangeForLine(line);
        ^string[start..end]
    }
    
    // document setup
    
    path_ { |apath|
        // this.notYetImplemented
    }
    
    dir {
        // var path = this.path;
        // ^path !? { path.dirname }
    }
    
    name {
        // this.deprecated(thisMethod, Document.findMethod(\title));
        // ^this.title
    }
    
    name_ { |aname|
        // this.deprecated(thisMethod, Document.findMethod(\title_));
        // this.title_(aname)
    }
    
    // envir stuff
    
    hasSavedPreviousEnvironment {
        // ^savedEnvir.notNil
    }
    
    envir_ { | newEnvir |
        //
        // envir = newEnvir;
        //
        // if(this.isFront) {
        // 	if(newEnvir.isNil) {
        // 		this.restorePreviousEnvironment
        // 	} {
        // 		if(this.hasSavedPreviousEnvironment.not) {
        // 			savedEnvir = currentEnvironment;
        // 		};
        // 		currentEnvironment = envir;
        // 	}
        // }
        
    }
    
    restorePreviousEnvironment { // happens on leaving focus
        // if (savedEnvir.notNil) {
        // 	currentEnvironment = savedEnvir;
        // 	savedEnvir = nil;
        // }
    }
    
    pushLinkedEnvironment { // happens on focus
        // if (envir.notNil) {
        // 	savedEnvir = currentEnvironment;
        // 	currentEnvironment = envir;
        // }
    }
    
    selectLine { | line |
        // var text, breaks, numLines, start = 0, end;
        // if(line < 1, { line = 1 });
        // text = this.text;
        // breaks = text.findAll("\n");
        // numLines = breaks.size + 1;
        // line = min(line, numLines);
        // if(line > 1, { start = breaks[line - 2] + 1});
        // end = breaks[line - 1] ?? { text.size };
        // this.selectRange(start, end - start);
    }
    
    selectRange { | start=0, length=0 |
        // this.prSetSelectionMirror(quuid, start, length); // set the backend mirror
        // ScIDE.setSelectionByQUuid(quuid, start, length); // set the IDE doc
    }
    
    editable_ { | bool=true |
        // editable = bool;
        // ScIDE.setEditablebyQUuid(quuid, bool);
    }
    
    promptToSave_ { | bool |
        // promptToSave = bool;
        // ScIDE.setPromptsToSavebyQUuid(quuid, bool);
    }
    
    removeUndo {
        // ScIDE.removeDocUndoByQUuid(quuid);
    }
    
    // probably still needed for compatibility
    *implementationClass { ^this }
}

LSPDocumentChange {
    var <>startLine, <>startChar, <>endLine, <>endChar, <>text;
    
    *new {
        |startLine, startChar, endLine, endChar, text|
        ^super.newCopyArgs(startLine, startChar, endLine, endChar, text)
    }
    
    *wholeDocument {
        |text|
        ^this.new(nil, nil, nil, nil, text)
    }
    
    isWholeDocument {
        ^[startLine, startChar, endLine, endChar].includes(nil)
    }
    
    stringIndices {
        |string|
        ^[
            string.lineCharToIndex(startLine, startChar),
            string.lineCharToIndex(endLine, endChar)
        ]
    }
}

+String {
    lineCharToIndex {
        |line, character|
        var currentIndex = 0;
        
        line.do {
            currentIndex = this.find("\n", false, currentIndex);
            
            if (currentIndex.isNil) {
                ^this.size
            } {
                currentIndex = currentIndex + 1
            }
        };
        
        ^(currentIndex + character)
    }
}










