import React from 'react';
import { ShieldAlert, RefreshCw, LayoutDashboard } from 'lucide-react';

interface ErrorBoundaryProps {
  children: React.ReactNode;
  fallbackTitle?: string;
}

interface ErrorBoundaryState {
  hasError: boolean;
  error: Error | null;
  errorInfo: React.ErrorInfo | null;
}

/**
 * ErrorBoundary — catches runtime errors in child component trees and
 * displays a user-friendly error card instead of a blank page.
 *
 * Usage:
 *   <ErrorBoundary>
 *     <YourPage />
 *   </ErrorBoundary>
 */
export class ErrorBoundary extends React.Component<ErrorBoundaryProps, ErrorBoundaryState> {
  constructor(props: ErrorBoundaryProps) {
    super(props);
    this.state = { hasError: false, error: null, errorInfo: null };
  }

  static getDerivedStateFromError(error: Error): Partial<ErrorBoundaryState> {
    return { hasError: true, error };
  }

  componentDidCatch(error: Error, errorInfo: React.ErrorInfo) {
    console.error('[ErrorBoundary] Caught runtime error:', error, errorInfo);
    this.setState({ errorInfo });
  }

  handleReload = () => {
    this.setState({ hasError: false, error: null, errorInfo: null });
    window.location.reload();
  };

  handleBack = () => {
    window.location.hash = '#/dashboard';
  };

  render() {
    if (!this.state.hasError) {
      return this.props.children;
    }

    const { fallbackTitle = 'Page Rendering Error' } = this.props;
    const errorMessage = this.state.error?.message || 'An unexpected error occurred.';

    return (
      <div className="min-h-screen bg-slate-950 flex items-center justify-center p-6 font-sans">
        <div className="w-full max-w-lg">
          {/* Glow accent */}
          <div className="absolute inset-0 pointer-events-none">
            <div className="absolute top-1/3 left-1/2 -translate-x-1/2 w-96 h-96 bg-rose-500/10 rounded-full blur-3xl" />
          </div>

          <div className="relative bg-slate-900/80 border border-rose-500/30 rounded-2xl p-8 shadow-2xl backdrop-blur-sm">
            {/* Top accent bar */}
            <div className="absolute top-0 left-0 right-0 h-[2px] bg-gradient-to-r from-transparent via-rose-500/60 to-transparent rounded-t-2xl" />

            {/* Icon + title */}
            <div className="flex items-center gap-4 mb-6">
              <div className="p-3 rounded-xl bg-rose-500/15 border border-rose-500/30 text-rose-400 shrink-0">
                <ShieldAlert size={28} />
              </div>
              <div>
                <h1 className="text-lg font-bold text-white tracking-tight">{fallbackTitle}</h1>
                <p className="text-xs text-slate-400 mt-0.5">
                  A runtime error prevented this page from rendering correctly.
                </p>
              </div>
            </div>

            {/* Error message */}
            <div className="bg-slate-950/80 border border-slate-800 rounded-xl p-4 mb-6 font-mono">
              <p className="text-[11px] text-rose-300 font-bold uppercase tracking-wider mb-2">Error Details</p>
              <p className="text-xs text-slate-300 leading-relaxed break-words">
                {errorMessage}
              </p>
            </div>

            {/* Guidance */}
            <p className="text-xs text-slate-400 mb-6 leading-relaxed">
              This error has been logged. You can try reloading the page or return to the 
              dashboard. If the issue persists, check the browser console for more details.
            </p>

            {/* Actions */}
            <div className="flex gap-3">
              <button
                onClick={this.handleReload}
                className="flex items-center gap-2 px-4 py-2.5 rounded-xl bg-slate-800 border border-slate-700 text-slate-200 text-xs font-bold hover:bg-slate-700 hover:border-slate-600 transition-all duration-200"
              >
                <RefreshCw size={14} />
                Reload Page
              </button>
              <button
                onClick={this.handleBack}
                className="flex items-center gap-2 px-4 py-2.5 rounded-xl bg-gradient-to-r from-blue-600 to-cyan-600 text-white text-xs font-bold hover:from-blue-500 hover:to-cyan-500 transition-all duration-200 shadow-lg shadow-blue-500/20"
              >
                <LayoutDashboard size={14} />
                Return to Dashboard
              </button>
            </div>
          </div>
        </div>
      </div>
    );
  }
}

export default ErrorBoundary;
