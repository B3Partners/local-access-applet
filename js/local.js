

function LocalAccess() {
    this.appletInitialized = false;
    
    this.CB = "LocalAccess_callback";
    this.E_CB = "LocalAccess_errorCallback";
}

var localAccessAppletLoaded = function() {};

LocalAccess.prototype.initApplet = function(callback) {
    if(this.appletInitialized) {
        return;
    }
    
    this.appletInitialized = true;
    
    if(callback) {
        localAccessAppletLoaded = callback;
    }
    
    var attributes = {
        id:         "applet",
        code:       "nl.b3p.applet.local.LocalAccessApplet",
        archive:    B3pCatalog.contextPath + "/applet/local-access-applet.jar",
        width:      $("#applet-container").width(),
        height:     $("#applet-container").height(),
        placeholder: "applet-container"
    };
    var parameters = { classloader_cache:"false", jnlp_href: B3pCatalog.contextPath + "/applet/launch.jnlp" }; 
    var version = "1.6"; 
    deployJava.runApplet(attributes, parameters, version);    
}

LocalAccess.prototype.registerCallbacks = function(callback, errorCallback) {
    this.callbacks[this.callbacksCounter] = { callback: callback, errorCallback: errorCallback };
    
    return this.callbacksCounter++;
}

// Because the applet uses a worker thread, these callbacks may be called out-of-order
var LocalAccess_callbacks = { };
var LocalAccess_callbackIndex = 0;

function LocalAccess_callback() {
    var requestId = arguments[0];
    var args = Array.prototype.slice.call(arguments,1);  
    LocalAccess_callbacks[requestId].success.apply(this, args);
    delete LocalAccess_callbacks[requestId];
}

function LocalAccess_errorCallback() {
    var requestId = arguments[0];
    var args = Array.prototype.slice.call(arguments,1);    
    LocalAccess_callbacks[requestId].error.apply(this, args);
    delete LocalAccess_callbacks[requestId];
}

LocalAccess.prototype.selectDirectory = function(title, callback, errorCallback) {
    var requestId = "sd" + LocalAccess_callbackIndex++;
    LocalAccess_callbacks[requestId] = { success: callback, error: errorCallback };
    document.getElementById("applet").selectDirectory(title, requestId, this.CB, this.E_CB);
}

LocalAccess.prototype.listDirectory = function(dir, callback, errorCallback) {
    var requestId = "ld" + LocalAccess_callbackIndex++;    
    LocalAccess_callbacks[requestId] = { success: callback, error: errorCallback };
    document.getElementById("applet").listDirectory(dir, requestId, this.CB, this.E_CB);
}

LocalAccess.prototype.readFileUTF8 = function(file, callback, errorCallback) {
    var requestId = "r" + LocalAccess_callbackIndex++;    
    LocalAccess_callbacks[requestId] = { success: callback, error: errorCallback };
    document.getElementById("applet").readFileUTF8(file, requestId, this.CB, this.E_CB);
}

function LocalAccess_notFoundCallback() {
    var requestId = arguments[0];
    var args = Array.prototype.slice.call(arguments,1);    
    LocalAccess_callbacks[requestId].notFound.apply(this, args);
    delete LocalAccess_callbacks[requestId];
}

LocalAccess.prototype.readFileIfExistsUTF8 = function(file, callback, notFoundCallback, errorCallback) {
    var requestId = "rfnf" + LocalAccess_callbackIndex++;    
    LocalAccess_callbacks[requestId] = { success: callback, error: errorCallback, notFound: notFoundCallback };
    document.getElementById("applet").readFileIfExistsUTF8(file, "LocalAccess_notFoundCallback", requestId, this.CB, this.E_CB);
}

LocalAccess.prototype.writeFileUTF8 = function(file, content, callback, errorCallback) {
    var requestId = "w" + LocalAccess_callbackIndex++;    
    LocalAccess_callbacks[requestId] = { success: callback, error: errorCallback };
    document.getElementById("applet").writeFileUTF8(file, content, requestId, this.CB, this.E_CB);
}
