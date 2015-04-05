package com.LunarCopter;

import android.graphics.Canvas;

class CopterThread extends Thread {
    private CopterView _view;
    private boolean _run = false;
    
   
    public CopterThread(CopterView view) {
        _view = view;
    }
    
    // Should the gameloop keep running
    public void setRunning(boolean run) {
        _run = run;
    }
    
    public boolean isRunning() {
        return _run;
    }
    
    @Override
    public void run() {
        Canvas c;
        _view.init();
        while (_run) {
            c = null;
            try {
                c = _view.getHolder().lockCanvas(null);
                synchronized (_view.getHolder()) {
                    _view.update();
                    _view.onDraw(c);
                }
            } finally {
                // Do this in a finally so that if an exception is thrown
                // during the above, we don't leave the Surface in an
                // inconsistent state
                if (c != null) {
                    _view.getHolder().unlockCanvasAndPost(c);
                }
            }
        }
    }
}