

function LocalAccess() {
    this.appletInitialized = false;
    this.callbacks = {};
}

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

var LocalAccess_callbacks = { success: function() {}, error: function() {} };

function LocalAccess_callback() {
    LocalAccess_callbacks.success.apply(this, arguments);
}

function LocalAccess_errorCallback() {
    LocalAccess_callbacks.error.apply(this, arguments);
}

LocalAccess.prototype.selectDirectory = function(title, callback, errorCallback) {
    LocalAccess_callbacks = { success: callback, error: errorCallback };
    document.getElementById("applet").selectDirectory(title, "LocalAccess_callback", "LocalAccess_errorCallback");
}

LocalAccess.prototype.listDirectory = function(dir, callback, errorCallback) {
    LocalAccess_callbacks = { success: callback, error: errorCallback };
    document.getElementById("applet").listDirectory(dir, "LocalAccess_callback", "LocalAccess_errorCallback");
}