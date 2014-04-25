var SolarNode = {
	
	/**
	 * Namespace for classes. 
	 * @namespace
	 */
	Class: {},
	
	/**
	 * Namespace for runtime state. 
	 * @namespace
	 */
	Runtime: {},

	/**
	 * Names to use for user-interaction events.
	 * 
	 * <p>On non-touch devices these equate to <em>mousedown</em>, 
	 * <em>mouseup</em>, etc. On touch-enabled devices these equate to
	 * <em>touchstart</em>, <em>touchend</em>, etc.</p>
	 */
	touchEventNames : {
		start: "mousedown",
		move: "mousemove",
		end: "mouseup",
		cancel: "touchcancel"
	},
	
	/**
	 * Flag indicating if the client supports touch events.
	 * 
	 * @returns {Boolean} <em>true</em> if have touch support
	 */
	hasTouchSupport : (function() {
		if ( 'createTouch' in document) { // True on the iPhone
			return true;
		}
		try {
			var event = document.createEvent('TouchEvent');
			return !!event.initTouchEvent;
		} catch( error ) {
			return false;
		}
	}()),
	
	hiRes : (window.devicePixelRatio === undefined ? false : window.devicePixelRatio > 1),

	anyEvent : function(event) {
		event.preventDefault();
		if ( event.targetTouches !== undefined ) {
			return (event.targetTouches.length > 0 ? event.targetTouches[0] : undefined);
		}
		return event;
	},
	
	/**
	 * Display an error alert message.
	 * 
	 * @param {Object} contents the HTML to display in the alert
	 */
	errorAlert : function(contents) {
		$('body').append(
				$('<div class="alert alert-error"><button class="close btn" data-dismiss="alert">&times;</button></div>')
				.append(contents));
	},
	
	/**
	 * Get a message based on a template, with optional parameters.
	 * 
	 * Parameters should be in the form {x} where x is a number starting at 0. Occurrences
	 * of these parameters in the message bundle will be replaced by corresponding
	 * array values from the passed in params array.
	 */
	i18n : function(msg, params) {
		if ( !Array.isArray(params) ) {
			params = [];
		}
		var i = 0;
		for ( i = 0; i < params.length; i++ ) {
			msg = msg.replace(new RegExp('\\{'+(i)+'\\}','g'),params[i]);
		}
		return msg;
	 },
	 
	 alert : function(title, message, style, container) {
		 container = container || $('#body-container');
		 if ( container ) {
			 var alert = $('<div class="alert"><button type="button" class="close" data-dismiss="alert">&times;</button></div>');
			 if ( title ) {
				 $('<strong/>').append(title).appendTo(alert);
				 if ( message ) {
					 $(' ').appendTo(alert);
				 }
			 }
			 if ( message ) {
				 alert.append(message);
			 }
			 if ( style ) {
				 alert.addClass(style);
			 }
			 container.prepend(alert);
		 }
	 },
	 
	 info : function(message, container) {
		 SolarNode.alert(null, message, 'alert-info', container);
	 },
	 
	 warn : function(title, message, container) {
		 SolarNode.alert(title, message, null, container);
	 },
	 
	 error : function(message, container) {
		 SolarNode.alert(null, message, 'alert-error', container);
	 }
	
};

SolarNode.touchEventNames = (function() {
	return SolarNode.hasTouchSupport ? {
			start: "touchstart",
			move: "touchmove",
			end: "touchend",
			cancel: "touchcancel"
		} : SolarNode.touchEventNames;
}());


/**
 * A UI slider.
 * @class
 */
SolarNode.Class.Slider = function(el, config) {
	var min = (isNaN(config.min) ? 0 : config.min);
	var max = (isNaN(config.max) ? 100 : config.max);
	var step = (isNaN(config.step) ? 1 : config.step);
	var value = (isNaN(config.value) ? 50 : 
		(config.value < min ? min : (config.value > max ? max : Number(config.value))));
	var handleWidth = (isNaN(config.handleWidth) ? 26 : config.handleWidth);
	var showValue = (config.showValue === true ? true : false);
	
	var ui = $(el);
	ui.addClass('ui-slider ui-slider-horizontal');
	ui.append('<div class="progress"><div class="ui-slider-range bar" /></div><button type="button" class="ui-slider-handle btn"/>')
		.append('<div class="slider-min label pull-left">'+min+'</div>')
		.append('<div class="slider-max label pull-right">'+max+'</div>');
	//ui.find('.progress').css('margin-right', (handleWidth/2)+'px');
	var uiRange = $(ui).find('.ui-slider-range');
	var uiHandle = $(ui).find('.ui-slider-handle').css({
			'width' : handleWidth + 'px',
			'margin-left' : '-'+(handleWidth/2+4)+'px'
		});
	
	var fnChange = (typeof config.change === 'function' ? config.change : undefined);
	
	var tracking = false;
	
	var layoutSubviews = function(percent) {
		if ( percent === undefined ) {
			percent = value / (max - min);
		}
		var barWidth = ui.width();
		var handleWidth = uiHandle.width();
		var usableWidth = barWidth - handleWidth;
		var offsetPercent = (handleWidth * 0.5) / barWidth;
		var viewPercent = ((usableWidth * percent) / barWidth) + offsetPercent;
		var cssPos = (100 * viewPercent)+'%';
		uiRange.css('width', cssPos);
		uiHandle.css('left', cssPos);
		if ( showValue === true ) {
			uiHandle.text(value);
		}
	};
	
	var setTracking = function(track) {
		tracking = (track === true ? true : false);
	};
	
	var handleEventMove = function(event) {
		if ( !tracking ) {
			return;
		}
		var barWidth = ui.width();
		var handleWidth = uiHandle.width();
		var usableWidth = barWidth - handleWidth;
		var percent = (event.clientX - ui.offset().left - handleWidth) / usableWidth;
		if ( percent > 1 ) {
			percent = 1;
		} else if ( percent < 0 ) {
			percent = 0;
		}
		var newValue = (max - min) * percent;
		if ( step > 0 ) {
			newValue = Math.round(newValue / step) * step;
		}
		var oldValue = value;
		value = newValue;
		layoutSubviews(percent);
		if ( fnChange !== undefined && newValue !== oldValue ) {
			fnChange.call(ui, event, {
				value: newValue,
				oldValue: oldValue,
				percent: percent
			});
		}
	};
	
	// bind events
	ui.bind(SolarNode.touchEventNames.start, function() {
		setTracking(true);
	}).bind(SolarNode.touchEventNames.cancel, function() {
		setTracking(false);
	}).bind(SolarNode.touchEventNames.end, function() {
		setTracking(false);
	}).bind(SolarNode.touchEventNames.move, handleEventMove);
	
	// initial layout
	layoutSubviews();
};

SolarNode.Class.Slider.prototype = {
	
};

SolarNode.context = (function() {
	var basePath = undefined;
	
	var contextPath = function() {
		if ( basePath === undefined ) {
			basePath = $('meta[name=base-path]').attr('content');
		}
		return (basePath === undefined ? "" : basePath);
	};
	
	var helper = {
		
		basePath : contextPath,
		
		path : function(path) {
			var p = contextPath();
			var p1 = String(path);
			if ( p.search(/\/$/) >= 0 && p1.length > 0 && p1.charAt(0) === '/' ) {
				p += p1.substring(1);
			} else {
				p += p1;
			}
			return p;
		}
		
	};
	
	return helper;
})();

SolarNode.showLoading = function(button) {
	var ladda = button.data('ladda');
	if ( ladda === undefined ) {
		button.button('loading');
		ladda = Ladda.create(button.get(0));
		button.data('ladda', ladda);
		ladda.start();
	}
};

SolarNode.hideLoading = function(button) {
	var ladda = button.data('ladda');
	if ( ladda !== undefined ) {
		button.button('reset');
		ladda.stop();
		button.removeData('ladda');
	}
};

$(document).ready(function() {
	$('body').on('hidden', '.modal.dynamic', function () {
		$(this).removeData('modal');
	});
});
