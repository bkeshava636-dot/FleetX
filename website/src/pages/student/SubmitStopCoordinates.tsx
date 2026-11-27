import React, { useState, useEffect, useMemo } from 'react';
import { Link } from 'react-router-dom';
import {
  ArrowLeft,
  MapPin,
  Navigation,
  AlertTriangle,
  CheckCircle2,
  Search,
  Check,
  RotateCcw,
  Info,
  ShieldCheck,
  Send,
  Sun,
  Moon,
} from 'lucide-react';
import logoUrl from '../../assets/logo/fleetx-logo.png';
import { subscribeToRoutes, naturalRouteSort, checkStopHasCoordinates } from '../../services/routesService';
import { submitStopCoordinates, calculateDistanceMeters } from '../../services/submissionService';
import { Route } from '../../types/admin';
import { LocationSource } from '../../types/submissions';
import { GoogleMapPicker, LatLng } from '../../components/maps/GoogleMapPicker';
import { LoadingSpinner } from '../../components/common/LoadingSpinner';
import { CoordinateTag, MapGridBackdrop } from '../../components/MobilityPrimitives';

export const SubmitStopCoordinates: React.FC = () => {
  const [theme, setTheme] = useState<'dark' | 'light-bw'>(() => {
    try {
      return localStorage.getItem('fleetx_landing_theme') === 'light-bw' ? 'light-bw' : 'dark';
    } catch {
      return 'dark';
    }
  });

  const toggleTheme = () => {
    setTheme((prev) => {
      const next = prev === 'dark' ? 'light-bw' : 'dark';
      try {
        localStorage.setItem('fleetx_landing_theme', next);
      } catch {
        // ignore storage errors
      }
      return next;
    });
  };

  // Routes & Selection State
  const [routes, setRoutes] = useState<Route[]>([]);
  const [loadingRoutes, setLoadingRoutes] = useState<boolean>(true);
  const [routesError, setRoutesError] = useState<string | null>(null);

  const [selectedRouteId, setSelectedRouteId] = useState<string>('');
  const [selectedStopId, setSelectedStopId] = useState<string>('');
  const [routeSearch, setRouteSearch] = useState<string>('');

  // Location Proposal State
  const [proposedLocation, setProposedLocation] = useState<LatLng | null>(null);
  const [locationSource, setLocationSource] = useState<LocationSource>('MANUAL');
  const [accuracyMeters, setAccuracyMeters] = useState<number | null>(null);
  const [locatingGps, setLocatingGps] = useState<boolean>(false);
  const [gpsError, setGpsError] = useState<string | null>(null);

  // Student remarks & form state
  const [studentNotes, setStudentNotes] = useState<string>('');
  const [submitting, setSubmitting] = useState<boolean>(false);
  const [submissionError, setSubmissionError] = useState<string | null>(null);
  const [submissionSuccess, setSubmissionSuccess] = useState<boolean>(false);
  const [submittedSubmissionId, setSubmittedSubmissionId] = useState<string | null>(null);

  // 1. Subscribe to Live Routes
  useEffect(() => {
    setLoadingRoutes(true);
    const unsubscribe = subscribeToRoutes(
      (data: Route[]) => {
        const sorted = [...data].sort(naturalRouteSort);
        setRoutes(sorted);
        setLoadingRoutes(false);
      },
      (err: Error) => {
        setRoutesError(err.message || 'Failed to load routes.');
        setLoadingRoutes(false);
      }
    );
    return () => unsubscribe();
  }, []);

  // Filtered Routes
  const filteredRoutes = useMemo(() => {
    if (!routeSearch.trim()) return routes;
    const term = routeSearch.toLowerCase();
    return routes.filter(
      (r) =>
        r.name.toLowerCase().includes(term) ||
        r.routeId.toLowerCase().includes(term) ||
        r.busId.toLowerCase().includes(term)
    );
  }, [routes, routeSearch]);

  // Selected Route Object
  const selectedRoute = useMemo(() => {
    return routes.find((r) => r.routeId === selectedRouteId) || null;
  }, [routes, selectedRouteId]);

  // Selected Stop Object
  const selectedStop = useMemo(() => {
    if (!selectedRoute) return null;
    return selectedRoute.stops.find((s) => s.stopId === selectedStopId) || null;
  }, [selectedRoute, selectedStopId]);

  // Existing stop coordinates
  const existingCoords = useMemo<LatLng | null>(() => {
    if (!selectedStop || !checkStopHasCoordinates(selectedStop)) return null;
    return { lat: selectedStop.latitude, lng: selectedStop.longitude };
  }, [selectedStop]);

  // When a stop is selected, preset proposed location if existing coordinates exist
  useEffect(() => {
    if (selectedStop) {
      if (checkStopHasCoordinates(selectedStop)) {
        setProposedLocation({ lat: selectedStop.latitude, lng: selectedStop.longitude });
        setLocationSource('MANUAL');
        setAccuracyMeters(null);
      } else {
        // Clear proposed location so user explicitly sets it
        setProposedLocation(null);
        setLocationSource('MANUAL');
        setAccuracyMeters(null);
      }
      setGpsError(null);
      setSubmissionError(null);
    }
  }, [selectedStopId]);

  // Distance from existing stop (if both present)
  const distanceFromExisting = useMemo(() => {
    if (!existingCoords || !proposedLocation) return null;
    return calculateDistanceMeters(
      existingCoords.lat,
      existingCoords.lng,
      proposedLocation.lat,
      proposedLocation.lng
    );
  }, [existingCoords, proposedLocation]);

  // 2. Browser GPS Geolocation Handler (On-demand only)
  const handleAcquireGpsLocation = () => {
    if (!navigator.geolocation) {
      setGpsError('Geolocation is not supported by your browser.');
      return;
    }

    setLocatingGps(true);
    setGpsError(null);

    navigator.geolocation.getCurrentPosition(
      (pos) => {
        const lat = Number(pos.coords.latitude.toFixed(6));
        const lng = Number(pos.coords.longitude.toFixed(6));
        const acc = Math.round(pos.coords.accuracy);

        setProposedLocation({ lat, lng });
        setLocationSource('GPS');
        setAccuracyMeters(acc);
        setLocatingGps(false);
      },
      (err) => {
        setLocatingGps(false);
        switch (err.code) {
          case err.PERMISSION_DENIED:
            setGpsError('Location permission was denied. Please enable location permissions or select the bus stop point manually on the map.');
            break;
          case err.POSITION_UNAVAILABLE:
            setGpsError('GPS position unavailable. Please ensure GPS/location services are turned on, or click the map.');
            break;
          case err.TIMEOUT:
            setGpsError('GPS request timed out. Please try again or mark the stop position manually.');
            break;
          default:
            setGpsError('Could not determine your GPS location. Please place the pin manually.');
        }
      },
      {
        enableHighAccuracy: true,
        timeout: 15000,
        maximumAge: 0,
      }
    );
  };

  // Map position change handler
  const handleMapPositionChange = (pos: LatLng) => {
    setProposedLocation(pos);
    setLocationSource('MANUAL');
    setAccuracyMeters(null);
    setGpsError(null);
  };

  // Form submission handler
  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedRoute || !selectedStop) {
      setSubmissionError('Please select a bus route and a stop.');
      return;
    }
    if (!proposedLocation) {
      setSubmissionError('Please select a location on the map or click "Use My Current Location".');
      return;
    }

    setSubmitting(true);
    setSubmissionError(null);

    try {
      const result = await submitStopCoordinates({
        routeId: selectedRoute.routeId,
        routeName: selectedRoute.name,
        stopId: selectedStop.stopId,
        stopName: selectedStop.name,
        stopOrder: selectedStop.order,
        proposedLatitude: proposedLocation.lat,
        proposedLongitude: proposedLocation.lng,
        accuracyMeters: accuracyMeters,
        source: locationSource,
        existingLatitude: selectedStop.latitude || null,
        existingLongitude: selectedStop.longitude || null,
        studentNotes: studentNotes.trim() || null,
      });

      setSubmittedSubmissionId(result.submissionId);
      setSubmissionSuccess(true);
      setSubmitting(false);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Submission failed. Please check your network connection and try again.';
      setSubmissionError(msg);
      setSubmitting(false);
    }
  };

  // Reset form to submit another stop
  const handleReset = () => {
    setSubmissionSuccess(false);
    setSubmittedSubmissionId(null);
    setSelectedStopId('');
    setProposedLocation(null);
    setLocationSource('MANUAL');
    setAccuracyMeters(null);
    setStudentNotes('');
    setSubmissionError(null);
    setGpsError(null);
  };

  return (
    <div
      className={`relative min-h-screen bg-fleet-navy text-fleet-offwhite flex flex-col overflow-hidden selection:bg-fleet-cyan selection:text-fleet-navy transition-colors duration-200 ${
        theme === 'light-bw' ? 'landing-theme-bw' : ''
      }`}
    >
      <MapGridBackdrop dark />

      {/* Top Navbar */}
      <header className="bg-fleet-navy/85 backdrop-blur-md border-b border-white/10 sticky top-0 z-30">
        <div className="max-w-4xl mx-auto px-4 h-16 flex items-center justify-between gap-2">
          <Link
            to="/"
            className="flex items-center gap-2 text-xs font-semibold text-fleet-muted hover:text-fleet-offwhite transition-colors py-1.5 px-2.5 rounded-lg hover:bg-white/[0.06]"
          >
            <ArrowLeft className="w-4 h-4" />
            <span>FleetX Home</span>
          </Link>

          <div className="flex items-center gap-2.5">
            <img src={logoUrl} alt="FleetX Logo" className="w-7 h-7 rounded-lg shadow-sm border border-white/10" />
            <div className="flex flex-col leading-none">
              <span className="font-display text-sm font-bold tracking-tight text-fleet-offwhite">
                Fleet<span className="text-fleet-cyan">X</span>
              </span>
              <span className="font-mono text-[9px] uppercase tracking-widest text-fleet-muted">
                Stop Portal
              </span>
            </div>
          </div>

          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={toggleTheme}
              aria-label={theme === 'dark' ? 'Switch to Light Black & White mode' : 'Switch to Dark mode'}
              className="inline-flex items-center gap-1.5 rounded-full border border-white/15 bg-fleet-deep px-2.5 py-1 text-[11px] font-semibold text-fleet-offwhite hover:border-fleet-cyan/40 transition-colors"
            >
              {theme === 'dark' ? (
                <>
                  <Sun className="w-3.5 h-3.5 text-fleet-lime" />
                  <span className="hidden sm:inline">B&amp;W</span>
                </>
              ) : (
                <>
                  <Moon className="w-3.5 h-3.5 text-fleet-cyan" />
                  <span className="hidden sm:inline">Dark</span>
                </>
              )}
            </button>

            <div className="hidden sm:flex items-center gap-1.5 font-mono text-[10px] uppercase tracking-wider font-semibold text-fleet-lime bg-fleet-lime/15 border border-fleet-lime/30 px-2.5 py-1 rounded-full">
              <ShieldCheck className="w-3.5 h-3.5" />
              <span>Crowdsourced</span>
            </div>
          </div>
        </div>
      </header>

      {/* Main Container */}
      <main className="relative z-10 flex-1 max-w-4xl w-full mx-auto p-4 sm:p-6 lg:p-8 space-y-6">
        {/* Success Confirmation State */}
        {submissionSuccess ? (
          <div className="bg-fleet-deep border border-white/10 rounded-3xl p-8 sm:p-10 text-center shadow-2xl space-y-6 animate-in fade-in zoom-in-95 duration-200">
            <div className="w-16 h-16 bg-fleet-lime/15 text-fleet-lime border border-fleet-lime/35 rounded-full flex items-center justify-center mx-auto shadow-inner">
              <CheckCircle2 className="w-9 h-9" />
            </div>

            <div className="space-y-2">
              <h2 className="font-display text-2xl font-bold tracking-tight text-fleet-offwhite">
                Coordinates Submitted for Review!
              </h2>
              <p className="text-sm text-fleet-muted max-w-lg mx-auto leading-relaxed">
                Thank you! Your proposed location for{' '}
                <span className="font-semibold text-fleet-offwhite">{selectedStop?.name}</span> on{' '}
                <span className="font-semibold text-fleet-cyan">{selectedRoute?.name}</span> has been received.
              </p>
            </div>

            <div className="bg-fleet-navy/90 border border-white/10 rounded-2xl p-5 max-w-md mx-auto text-left text-xs space-y-2.5">
              <div className="flex justify-between items-center text-fleet-muted">
                <span>Proposed Coordinates</span>
                <span className="font-mono font-semibold text-fleet-offwhite">
                  {proposedLocation?.lat.toFixed(6)}, {proposedLocation?.lng.toFixed(6)}
                </span>
              </div>
              <div className="flex justify-between items-center text-fleet-muted">
                <span>Location Source</span>
                <span className="font-semibold text-fleet-offwhite">
                  {locationSource === 'GPS' ? `GPS (±${accuracyMeters}m)` : 'Manual Map Pin'}
                </span>
              </div>
              {submittedSubmissionId && (
                <div className="flex justify-between items-center text-fleet-muted border-t border-white/10 pt-2">
                  <span>Submission ID</span>
                  <span className="font-mono text-[11px] text-fleet-muted">{submittedSubmissionId}</span>
                </div>
              )}
            </div>

            {/* Explanation of Review Requirement */}
            <div className="bg-fleet-cyan/10 border border-fleet-cyan/30 rounded-2xl p-4 max-w-lg mx-auto text-left flex items-start gap-3 text-xs text-fleet-offwhite/90 leading-relaxed">
              <Info className="w-5 h-5 text-fleet-cyan flex-shrink-0 mt-0.5" />
              <div>
                <span className="font-display font-bold text-fleet-offwhite block mb-0.5">Administrator Approval Required</span>
                Coordinates do not go live automatically. Transport administrators will verify this coordinate against the official bus route before it updates on the FleetX Android app.
              </div>
            </div>

            <div className="flex flex-col sm:flex-row items-center justify-center gap-3 pt-2">
              <button
                type="button"
                onClick={handleReset}
                className="w-full sm:w-auto inline-flex items-center justify-center gap-2 px-6 py-2.5 bg-fleet-lime hover:bg-fleet-lime/90 text-fleet-navy text-xs font-bold rounded-xl transition shadow-lg"
              >
                <RotateCcw className="w-4 h-4" />
                <span>Submit Another Stop</span>
              </button>
              <Link
                to="/"
                className="w-full sm:w-auto inline-flex items-center justify-center px-6 py-2.5 bg-fleet-navy border border-white/15 hover:border-fleet-cyan/40 text-fleet-offwhite text-xs font-semibold rounded-xl transition"
              >
                Return to FleetX Home
              </Link>
            </div>
          </div>
        ) : (
          /* Submission Workflow Form */
          <form onSubmit={handleSubmit} className="space-y-6">
            {/* Header Hero Banner */}
            <div className="bg-fleet-deep rounded-3xl p-6 sm:p-8 border border-white/10 shadow-xl relative overflow-hidden">
              <div className="flex flex-col sm:flex-row sm:items-start gap-4">
                <div className="w-12 h-12 bg-fleet-cyan/15 text-fleet-cyan border border-fleet-cyan/30 rounded-2xl flex items-center justify-center flex-shrink-0">
                  <MapPin className="w-6 h-6" />
                </div>
                <div className="space-y-2">
                  <CoordinateTag label="STOP COORDINATE PORTAL" />
                  <h1 className="font-display text-2xl sm:text-3xl font-bold text-fleet-offwhite tracking-tight">
                    Submit Bus Stop Coordinates
                  </h1>
                  <p className="text-xs sm:text-sm text-fleet-muted leading-relaxed max-w-2xl">
                    Help your fellow students by providing exact boarding coordinates for college bus stops. Mark the public roadside pickup point on Google Maps for administrator review.
                  </p>
                </div>
              </div>
            </div>

            {/* Crucial Student Instruction Warning Banner */}
            <div className="bg-amber-950/40 border border-amber-500/40 rounded-2xl p-4 flex items-start gap-3.5 text-amber-200 text-xs leading-relaxed">
              <AlertTriangle className="w-5 h-5 text-amber-400 flex-shrink-0 mt-0.5" />
              <div>
                <strong className="text-amber-100 font-semibold block mb-0.5">
                  Important: Mark the Public Roadside Bus Stop
                </strong>
                Please mark the actual roadside pickup spot where the bus physically halts. Do{' '}
                <strong className="underline text-amber-300">not</strong> mark your personal residence or private home.
              </div>
            </div>

            {/* Error Banners */}
            {routesError && (
              <div className="bg-red-950/40 border border-red-500/40 rounded-2xl p-4 text-xs text-red-200 flex items-center gap-3">
                <AlertTriangle className="w-5 h-5 text-red-400 flex-shrink-0" />
                <span>{routesError}</span>
              </div>
            )}
            {submissionError && (
              <div className="bg-red-950/40 border border-red-500/40 rounded-2xl p-4 text-xs text-red-200 flex items-center gap-3">
                <AlertTriangle className="w-5 h-5 text-red-400 flex-shrink-0" />
                <span>{submissionError}</span>
              </div>
            )}

            {/* Step 1: Select Route */}
            <div className="bg-fleet-deep rounded-2xl p-6 border border-white/10 shadow-sm space-y-4">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2.5">
                  <span className="w-6 h-6 rounded-full bg-fleet-cyan/15 text-fleet-cyan font-mono font-bold text-xs flex items-center justify-center border border-fleet-cyan/35">
                    01
                  </span>
                  <h2 className="font-display text-sm sm:text-base font-bold text-fleet-offwhite">Select Bus Route</h2>
                </div>
                {loadingRoutes && <LoadingSpinner label="Loading routes..." size="sm" />}
              </div>

              <div className="relative">
                <Search className="w-4 h-4 text-fleet-muted absolute left-3.5 top-1/2 -translate-y-1/2 pointer-events-none" />
                <input
                  type="text"
                  placeholder="Search route by name, route ID, or bus number..."
                  value={routeSearch}
                  onChange={(e) => setRouteSearch(e.target.value)}
                  className="w-full pl-10 pr-4 py-2.5 bg-fleet-navy border border-white/15 rounded-xl text-xs text-fleet-offwhite placeholder:text-fleet-muted/70 focus:outline-none focus:border-fleet-cyan"
                />
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 gap-2.5 max-h-56 overflow-y-auto pr-1">
                {filteredRoutes.map((route) => {
                  const isSelected = route.routeId === selectedRouteId;
                  return (
                    <button
                      key={route.routeId}
                      type="button"
                      onClick={() => {
                        setSelectedRouteId(route.routeId);
                        setSelectedStopId('');
                      }}
                      className={`text-left p-3 rounded-xl border text-xs transition flex flex-col justify-between ${
                        isSelected
                          ? 'bg-fleet-cyan/15 border-fleet-cyan text-fleet-offwhite font-semibold ring-1 ring-fleet-cyan/40'
                          : 'bg-fleet-navy/80 border-white/10 text-fleet-muted hover:bg-fleet-navy hover:border-fleet-cyan/40'
                      }`}
                    >
                      <div className="flex items-center justify-between">
                        <span className="font-mono text-[11px] text-fleet-cyan">{route.routeId}</span>
                        {isSelected && <Check className="w-3.5 h-3.5 text-fleet-lime" />}
                      </div>
                      <span className="font-semibold text-fleet-offwhite truncate mt-1">{route.name}</span>
                      <div className="flex items-center justify-between text-[10px] text-fleet-muted mt-2 pt-1 border-t border-white/10">
                        <span>Bus: {route.busId || 'N/A'}</span>
                        <span>{route.stops.length} stops</span>
                      </div>
                    </button>
                  );
                })}
              </div>
            </div>

            {/* Step 2: Select Stop */}
            {selectedRoute && (
              <div className="bg-fleet-deep rounded-2xl p-6 border border-white/10 shadow-sm space-y-4">
                <div className="flex items-center gap-2.5">
                  <span className="w-6 h-6 rounded-full bg-fleet-cyan/15 text-fleet-cyan font-mono font-bold text-xs flex items-center justify-center border border-fleet-cyan/35">
                    02
                  </span>
                  <h2 className="font-display text-sm sm:text-base font-bold text-fleet-offwhite">Select Bus Stop</h2>
                  <span className="text-xs text-fleet-muted font-normal">
                    (Route: {selectedRoute.name})
                  </span>
                </div>

                <div className="grid grid-cols-1 sm:grid-cols-2 gap-2.5 max-h-60 overflow-y-auto pr-1">
                  {selectedRoute.stops.map((stop) => {
                    const isSelected = stop.stopId === selectedStopId;
                    const hasCoords = checkStopHasCoordinates(stop);

                    return (
                      <button
                        key={stop.stopId}
                        type="button"
                        onClick={() => setSelectedStopId(stop.stopId)}
                        className={`text-left p-3 rounded-xl border text-xs transition flex items-center justify-between ${
                          isSelected
                            ? 'bg-fleet-cyan/15 border-fleet-cyan text-fleet-offwhite font-semibold ring-1 ring-fleet-cyan/40'
                            : 'bg-fleet-navy/80 border-white/10 text-fleet-muted hover:bg-fleet-navy hover:border-fleet-cyan/40'
                        }`}
                      >
                        <div className="flex items-center gap-2.5 min-w-0">
                          <span className="w-5 h-5 rounded-md bg-fleet-navy border border-white/10 text-fleet-muted text-[10px] font-mono flex items-center justify-center flex-shrink-0">
                            {stop.order}
                          </span>
                          <div className="truncate">
                            <span className="font-semibold text-fleet-offwhite block truncate">{stop.name}</span>
                            <span className="text-[10px] font-mono text-fleet-muted">{stop.stopId}</span>
                          </div>
                        </div>

                        <div className="flex items-center gap-2 flex-shrink-0 ml-2">
                          {hasCoords ? (
                            <span className="text-[10px] font-semibold text-fleet-lime bg-fleet-lime/15 border border-fleet-lime/30 px-2 py-0.5 rounded-md">
                              Mapped
                            </span>
                          ) : (
                            <span className="text-[10px] font-semibold text-amber-400 bg-amber-950/60 border border-amber-500/30 px-2 py-0.5 rounded-md">
                              Missing Coords
                            </span>
                          )}
                          {isSelected && <Check className="w-3.5 h-3.5 text-fleet-lime" />}
                        </div>
                      </button>
                    );
                  })}
                </div>

                {/* Selected Stop Details */}
                {selectedStop && (
                  <div className="bg-fleet-navy/90 border border-white/10 rounded-xl p-3.5 flex flex-wrap items-center justify-between gap-3 text-xs">
                    <div>
                      <span className="text-fleet-muted block text-[11px]">Selected Stop</span>
                      <strong className="text-fleet-offwhite font-semibold">{selectedStop.name}</strong>
                    </div>
                    <div>
                      <span className="text-fleet-muted block text-[11px]">Current Coordinates</span>
                      {checkStopHasCoordinates(selectedStop) ? (
                        <span className="font-mono text-fleet-lime font-semibold">
                          {selectedStop.latitude.toFixed(6)}, {selectedStop.longitude.toFixed(6)}
                        </span>
                      ) : (
                        <span className="text-amber-400 font-semibold">None recorded</span>
                      )}
                    </div>
                  </div>
                )}
              </div>
            )}

            {/* Step 3: Location Selection via Google Maps & GPS */}
            {selectedStop && (
              <div className="bg-fleet-deep rounded-2xl p-6 border border-white/10 shadow-sm space-y-4">
                <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
                  <div className="flex items-center gap-2.5">
                    <span className="w-6 h-6 rounded-full bg-fleet-cyan/15 text-fleet-cyan font-mono font-bold text-xs flex items-center justify-center border border-fleet-cyan/35">
                      03
                    </span>
                    <h2 className="font-display text-sm sm:text-base font-bold text-fleet-offwhite">Determine Boarding Point</h2>
                  </div>

                  {/* Location Action Buttons */}
                  <div className="flex items-center gap-2">
                    <button
                      type="button"
                      disabled={locatingGps}
                      onClick={handleAcquireGpsLocation}
                      className="inline-flex items-center gap-1.5 px-3.5 py-2 bg-fleet-cyan hover:bg-fleet-cyan/90 disabled:opacity-50 text-fleet-navy text-xs font-bold rounded-xl transition shadow"
                    >
                      <Navigation className={`w-3.5 h-3.5 ${locatingGps ? 'animate-spin' : ''}`} />
                      <span>{locatingGps ? 'Acquiring GPS...' : 'Use My Location'}</span>
                    </button>
                  </div>
                </div>

                {gpsError && (
                  <div className="bg-red-950/40 border border-red-500/40 rounded-xl p-3 text-xs text-red-200 flex items-start gap-2.5">
                    <AlertTriangle className="w-4 h-4 text-red-400 flex-shrink-0 mt-0.5" />
                    <span>{gpsError}</span>
                  </div>
                )}

                {/* Google Maps Container */}
                <GoogleMapPicker
                  proposedPosition={proposedLocation}
                  existingPosition={existingCoords}
                  existingStopName={selectedStop.name}
                  accuracyMeters={accuracyMeters}
                  onPositionChange={handleMapPositionChange}
                  interactive={true}
                  draggable={true}
                  height="380px"
                />

                {/* Coordinates Readout & Method Pill */}
                <div className="bg-fleet-navy border border-white/10 rounded-xl p-4 grid grid-cols-1 sm:grid-cols-3 gap-3 text-xs">
                  <div>
                    <span className="text-[11px] text-fleet-muted block mb-1">Proposed Latitude</span>
                    <input
                      type="number"
                      step="0.000001"
                      placeholder="e.g. 15.168300"
                      value={proposedLocation ? proposedLocation.lat : ''}
                      onChange={(e) => {
                        const val = parseFloat(e.target.value);
                        setProposedLocation((prev) => ({
                          lat: isNaN(val) ? 0 : val,
                          lng: prev ? prev.lng : 0,
                        }));
                        setLocationSource('MANUAL');
                      }}
                      className="w-full bg-fleet-deep border border-white/15 rounded-lg px-2.5 py-1.5 text-xs font-mono text-fleet-offwhite focus:outline-none focus:border-fleet-cyan"
                    />
                  </div>

                  <div>
                    <span className="text-[11px] text-fleet-muted block mb-1">Proposed Longitude</span>
                    <input
                      type="number"
                      step="0.000001"
                      placeholder="e.g. 76.831700"
                      value={proposedLocation ? proposedLocation.lng : ''}
                      onChange={(e) => {
                        const val = parseFloat(e.target.value);
                        setProposedLocation((prev) => ({
                          lat: prev ? prev.lat : 0,
                          lng: isNaN(val) ? 0 : val,
                        }));
                        setLocationSource('MANUAL');
                      }}
                      className="w-full bg-fleet-deep border border-white/15 rounded-lg px-2.5 py-1.5 text-xs font-mono text-fleet-offwhite focus:outline-none focus:border-fleet-cyan"
                    />
                  </div>

                  <div className="flex flex-col justify-end">
                    <span className="text-[11px] text-fleet-muted block mb-1">Method & Accuracy</span>
                    <div className="flex items-center gap-2">
                      <span
                        className={`text-[11px] font-semibold px-2.5 py-1 rounded-lg border ${
                          locationSource === 'GPS'
                            ? 'bg-fleet-cyan/15 border-fleet-cyan/40 text-fleet-cyan'
                            : 'bg-fleet-deep border-white/15 text-fleet-offwhite'
                        }`}
                      >
                        {locationSource === 'GPS' ? 'GPS Device' : 'Manual Map Pin'}
                      </span>
                      {accuracyMeters && (
                        <span className="text-[11px] text-fleet-muted font-mono">
                          ±{accuracyMeters}m
                        </span>
                      )}
                    </div>
                  </div>
                </div>

                {distanceFromExisting !== null && (
                  <div className="text-[11px] text-fleet-muted bg-fleet-navy/80 border border-white/10 px-3 py-2 rounded-lg flex items-center justify-between">
                    <span>Distance from previously recorded coordinates:</span>
                    <strong className="text-fleet-offwhite font-mono">
                      {distanceFromExisting} metres
                    </strong>
                  </div>
                )}

                {/* Optional Student Remarks */}
                <div>
                  <label htmlFor="notes" className="text-xs font-medium text-fleet-offwhite block mb-1">
                    Landmark or Boarding Notes (Optional)
                  </label>
                  <textarea
                    id="notes"
                    rows={2}
                    placeholder="e.g. Near the HP petrol pump, under the blue bus shelter..."
                    value={studentNotes}
                    onChange={(e) => setStudentNotes(e.target.value)}
                    className="w-full bg-fleet-navy border border-white/15 rounded-xl p-3 text-xs text-fleet-offwhite placeholder:text-fleet-muted/70 focus:outline-none focus:border-fleet-cyan"
                  />
                </div>

                {/* Submit Action */}
                <div className="pt-2">
                  <button
                    type="submit"
                    disabled={submitting || !proposedLocation}
                    className="w-full inline-flex items-center justify-center gap-2 px-6 py-3 bg-fleet-lime hover:bg-fleet-lime/90 disabled:opacity-50 text-fleet-navy text-xs font-bold rounded-xl transition shadow-lg"
                  >
                    <Send className={`w-4 h-4 ${submitting ? 'animate-pulse' : ''}`} />
                    <span>{submitting ? 'Submitting Coordinates...' : 'Submit Coordinates for Review'}</span>
                  </button>
                </div>
              </div>
            )}
          </form>
        )}
      </main>
    </div>
  );
};

export default SubmitStopCoordinates;
