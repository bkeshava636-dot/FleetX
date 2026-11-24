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
} from 'lucide-react';
import logoUrl from '../../assets/logo/fleetx-logo.png';
import { subscribeToRoutes, naturalRouteSort, checkStopHasCoordinates } from '../../services/routesService';
import { submitStopCoordinates, calculateDistanceMeters } from '../../services/submissionService';
import { Route } from '../../types/admin';
import { LocationSource } from '../../types/submissions';
import { GoogleMapPicker, LatLng } from '../../components/maps/GoogleMapPicker';
import { LoadingSpinner } from '../../components/common/LoadingSpinner';

export const SubmitStopCoordinates: React.FC = () => {
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
    <div className="min-h-screen bg-slate-900 text-slate-100 flex flex-col selection:bg-blue-600 selection:text-white">
      {/* Top Navbar */}
      <header className="bg-slate-900/90 backdrop-blur-md border-b border-slate-800 sticky top-0 z-30">
        <div className="max-w-4xl mx-auto px-4 h-16 flex items-center justify-between">
          <Link
            to="/"
            className="flex items-center gap-2 text-xs font-semibold text-slate-400 hover:text-white transition-colors py-1.5 px-2.5 rounded-lg hover:bg-slate-800"
          >
            <ArrowLeft className="w-4 h-4" />
            <span>FleetX Home</span>
          </Link>

          <div className="flex items-center gap-2.5">
            <img src={logoUrl} alt="FleetX Logo" className="w-7 h-7 rounded-lg shadow-sm" />
            <span className="text-sm font-bold tracking-tight text-white">
              Fleet<span className="text-blue-500">X</span> Stop Portal
            </span>
          </div>

          <div className="flex items-center gap-1.5 text-[11px] font-medium text-emerald-400 bg-emerald-950/60 border border-emerald-500/30 px-2.5 py-1 rounded-full">
            <ShieldCheck className="w-3.5 h-3.5" />
            <span>Crowdsourced</span>
          </div>
        </div>
      </header>

      {/* Main Container */}
      <main className="flex-1 max-w-4xl w-full mx-auto p-4 sm:p-6 lg:p-8 space-y-6">
        {/* Success Confirmation State */}
        {submissionSuccess ? (
          <div className="bg-slate-800/90 border border-slate-700/80 rounded-3xl p-8 sm:p-10 text-center shadow-2xl space-y-6 animate-in fade-in zoom-in-95 duration-200">
            <div className="w-16 h-16 bg-emerald-500/10 text-emerald-400 border border-emerald-500/30 rounded-full flex items-center justify-center mx-auto shadow-inner">
              <CheckCircle2 className="w-9 h-9" />
            </div>

            <div className="space-y-2">
              <h2 className="text-2xl font-bold tracking-tight text-white">
                Coordinates Submitted for Review!
              </h2>
              <p className="text-sm text-slate-300 max-w-lg mx-auto leading-relaxed">
                Thank you! Your proposed location for{' '}
                <span className="font-semibold text-white">{selectedStop?.name}</span> on{' '}
                <span className="font-semibold text-blue-400">{selectedRoute?.name}</span> has been received.
              </p>
            </div>

            <div className="bg-slate-900/80 border border-slate-700/60 rounded-2xl p-5 max-w-md mx-auto text-left text-xs space-y-2.5">
              <div className="flex justify-between items-center text-slate-400">
                <span>Proposed Coordinates</span>
                <span className="font-mono font-semibold text-slate-200">
                  {proposedLocation?.lat.toFixed(6)}, {proposedLocation?.lng.toFixed(6)}
                </span>
              </div>
              <div className="flex justify-between items-center text-slate-400">
                <span>Location Source</span>
                <span className="font-semibold text-slate-200">
                  {locationSource === 'GPS' ? `GPS (±${accuracyMeters}m)` : 'Manual Map Pin'}
                </span>
              </div>
              {submittedSubmissionId && (
                <div className="flex justify-between items-center text-slate-400 border-t border-slate-800 pt-2">
                  <span>Submission ID</span>
                  <span className="font-mono text-[11px] text-slate-400">{submittedSubmissionId}</span>
                </div>
              )}
            </div>

            {/* Explanation of Review Requirement */}
            <div className="bg-blue-950/40 border border-blue-500/30 rounded-2xl p-4 max-w-lg mx-auto text-left flex items-start gap-3 text-xs text-blue-200/90 leading-relaxed">
              <Info className="w-5 h-5 text-blue-400 flex-shrink-0 mt-0.5" />
              <div>
                <span className="font-bold text-white block mb-0.5">Administrator Approval Required</span>
                Coordinates do not go live automatically. Transport administrators will verify this coordinate against the official bus route before it updates on the FleetX Android app.
              </div>
            </div>

            <div className="flex flex-col sm:flex-row items-center justify-center gap-3 pt-2">
              <button
                type="button"
                onClick={handleReset}
                className="w-full sm:w-auto inline-flex items-center justify-center gap-2 px-6 py-2.5 bg-blue-600 hover:bg-blue-500 text-white text-xs font-semibold rounded-xl transition shadow-lg shadow-blue-900/30"
              >
                <RotateCcw className="w-4 h-4" />
                <span>Submit Another Stop</span>
              </button>
              <Link
                to="/"
                className="w-full sm:w-auto inline-flex items-center justify-center px-6 py-2.5 bg-slate-700 hover:bg-slate-600 text-slate-200 text-xs font-semibold rounded-xl transition"
              >
                Return to FleetX Home
              </Link>
            </div>
          </div>
        ) : (
          /* Submission Workflow Form */
          <form onSubmit={handleSubmit} className="space-y-6">
            {/* Header Hero Banner */}
            <div className="bg-gradient-to-br from-slate-800/90 via-slate-800/70 to-slate-900/90 rounded-3xl p-6 sm:p-8 border border-slate-700/70 shadow-xl relative overflow-hidden">
              <div className="flex items-start gap-4">
                <div className="p-3.5 bg-blue-600/20 text-blue-400 border border-blue-500/30 rounded-2xl flex-shrink-0 shadow-inner">
                  <MapPin className="w-7 h-7" />
                </div>
                <div>
                  <h1 className="text-xl sm:text-2xl font-bold text-white tracking-tight">
                    Submit Bus Stop Coordinates
                  </h1>
                  <p className="text-xs sm:text-sm text-slate-400 mt-1.5 leading-relaxed max-w-2xl">
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
            <div className="bg-slate-800/80 rounded-2xl p-6 border border-slate-700/60 shadow-sm space-y-4">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <span className="w-6 h-6 rounded-full bg-blue-600/30 text-blue-400 font-bold text-xs flex items-center justify-center border border-blue-500/40">
                    1
                  </span>
                  <h2 className="text-sm font-bold text-white">Select Bus Route</h2>
                </div>
                {loadingRoutes && <LoadingSpinner label="Loading routes..." size="sm" />}
              </div>

              <div className="relative">
                <Search className="w-4 h-4 text-slate-400 absolute left-3.5 top-1/2 -translate-y-1/2 pointer-events-none" />
                <input
                  type="text"
                  placeholder="Search route by name, route ID, or bus number..."
                  value={routeSearch}
                  onChange={(e) => setRouteSearch(e.target.value)}
                  className="w-full pl-10 pr-4 py-2 bg-slate-900 border border-slate-700 rounded-xl text-xs text-slate-200 placeholder:text-slate-500 focus:outline-none focus:border-blue-500"
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
                          ? 'bg-blue-600/20 border-blue-500 text-white font-semibold ring-1 ring-blue-500/50'
                          : 'bg-slate-900/60 border-slate-700/60 text-slate-300 hover:bg-slate-800/80 hover:border-slate-600'
                      }`}
                    >
                      <div className="flex items-center justify-between">
                        <span className="font-mono text-[11px] text-blue-400">{route.routeId}</span>
                        {isSelected && <Check className="w-3.5 h-3.5 text-blue-400" />}
                      </div>
                      <span className="font-semibold text-slate-100 truncate mt-1">{route.name}</span>
                      <div className="flex items-center justify-between text-[10px] text-slate-400 mt-2 pt-1 border-t border-slate-700/40">
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
              <div className="bg-slate-800/80 rounded-2xl p-6 border border-slate-700/60 shadow-sm space-y-4">
                <div className="flex items-center gap-2">
                  <span className="w-6 h-6 rounded-full bg-blue-600/30 text-blue-400 font-bold text-xs flex items-center justify-center border border-blue-500/40">
                    2
                  </span>
                  <h2 className="text-sm font-bold text-white">Select Bus Stop</h2>
                  <span className="text-xs text-slate-400 font-normal">
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
                            ? 'bg-blue-600/20 border-blue-500 text-white font-semibold ring-1 ring-blue-500/50'
                            : 'bg-slate-900/60 border-slate-700/60 text-slate-300 hover:bg-slate-800/80 hover:border-slate-600'
                        }`}
                      >
                        <div className="flex items-center gap-2.5 min-w-0">
                          <span className="w-5 h-5 rounded-md bg-slate-800 text-slate-400 text-[10px] font-mono flex items-center justify-center flex-shrink-0">
                            {stop.order}
                          </span>
                          <div className="truncate">
                            <span className="font-semibold text-slate-200 block truncate">{stop.name}</span>
                            <span className="text-[10px] font-mono text-slate-500">{stop.stopId}</span>
                          </div>
                        </div>

                        <div className="flex items-center gap-2 flex-shrink-0 ml-2">
                          {hasCoords ? (
                            <span className="text-[10px] font-semibold text-emerald-400 bg-emerald-950/60 border border-emerald-500/30 px-2 py-0.5 rounded-md">
                              Mapped
                            </span>
                          ) : (
                            <span className="text-[10px] font-semibold text-amber-400 bg-amber-950/60 border border-amber-500/30 px-2 py-0.5 rounded-md">
                              Missing Coords
                            </span>
                          )}
                          {isSelected && <Check className="w-3.5 h-3.5 text-blue-400" />}
                        </div>
                      </button>
                    );
                  })}
                </div>

                {/* Selected Stop Details */}
                {selectedStop && (
                  <div className="bg-slate-900/90 border border-slate-700/80 rounded-xl p-3.5 flex flex-wrap items-center justify-between gap-3 text-xs">
                    <div>
                      <span className="text-slate-400 block text-[11px]">Selected Stop</span>
                      <strong className="text-white font-semibold">{selectedStop.name}</strong>
                    </div>
                    <div>
                      <span className="text-slate-400 block text-[11px]">Current Coordinates</span>
                      {checkStopHasCoordinates(selectedStop) ? (
                        <span className="font-mono text-emerald-400 font-semibold">
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
              <div className="bg-slate-800/80 rounded-2xl p-6 border border-slate-700/60 shadow-sm space-y-4">
                <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
                  <div className="flex items-center gap-2">
                    <span className="w-6 h-6 rounded-full bg-blue-600/30 text-blue-400 font-bold text-xs flex items-center justify-center border border-blue-500/40">
                      3
                    </span>
                    <h2 className="text-sm font-bold text-white">Determine Boarding Point</h2>
                  </div>

                  {/* Location Action Buttons */}
                  <div className="flex items-center gap-2">
                    <button
                      type="button"
                      disabled={locatingGps}
                      onClick={handleAcquireGpsLocation}
                      className="inline-flex items-center gap-1.5 px-3 py-1.5 bg-blue-600 hover:bg-blue-500 disabled:opacity-50 text-white text-xs font-semibold rounded-xl transition shadow"
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
                <div className="bg-slate-900 border border-slate-700/80 rounded-xl p-4 grid grid-cols-1 sm:grid-cols-3 gap-3 text-xs">
                  <div>
                    <span className="text-[11px] text-slate-400 block mb-1">Proposed Latitude</span>
                    <input
                      type="number"
                      step="0.000001"
                      placeholder="e.g. 12.971600"
                      value={proposedLocation ? proposedLocation.lat : ''}
                      onChange={(e) => {
                        const val = parseFloat(e.target.value);
                        setProposedLocation((prev) => ({
                          lat: isNaN(val) ? 0 : val,
                          lng: prev ? prev.lng : 0,
                        }));
                        setLocationSource('MANUAL');
                      }}
                      className="w-full bg-slate-800 border border-slate-700 rounded-lg px-2.5 py-1.5 text-xs font-mono text-slate-200 focus:outline-none focus:border-blue-500"
                    />
                  </div>

                  <div>
                    <span className="text-[11px] text-slate-400 block mb-1">Proposed Longitude</span>
                    <input
                      type="number"
                      step="0.000001"
                      placeholder="e.g. 77.594600"
                      value={proposedLocation ? proposedLocation.lng : ''}
                      onChange={(e) => {
                        const val = parseFloat(e.target.value);
                        setProposedLocation((prev) => ({
                          lat: prev ? prev.lat : 0,
                          lng: isNaN(val) ? 0 : val,
                        }));
                        setLocationSource('MANUAL');
                      }}
                      className="w-full bg-slate-800 border border-slate-700 rounded-lg px-2.5 py-1.5 text-xs font-mono text-slate-200 focus:outline-none focus:border-blue-500"
                    />
                  </div>

                  <div className="flex flex-col justify-end">
                    <span className="text-[11px] text-slate-400 block mb-1">Method & Accuracy</span>
                    <div className="flex items-center gap-2">
                      <span
                        className={`text-[11px] font-semibold px-2.5 py-1 rounded-lg border ${
                          locationSource === 'GPS'
                            ? 'bg-blue-950/60 border-blue-500/40 text-blue-300'
                            : 'bg-slate-800 border-slate-700 text-slate-300'
                        }`}
                      >
                        {locationSource === 'GPS' ? 'GPS Device' : 'Manual Map Pin'}
                      </span>
                      {accuracyMeters && (
                        <span className="text-[11px] text-slate-400 font-mono">
                          ±{accuracyMeters}m
                        </span>
                      )}
                    </div>
                  </div>
                </div>

                {distanceFromExisting !== null && (
                  <div className="text-[11px] text-slate-400 bg-slate-900/60 border border-slate-800 px-3 py-2 rounded-lg flex items-center justify-between">
                    <span>Distance from previously recorded coordinates:</span>
                    <strong className="text-slate-200 font-mono">
                      {distanceFromExisting} metres
                    </strong>
                  </div>
                )}

                {/* Optional Student Remarks */}
                <div>
                  <label htmlFor="notes" className="text-xs font-medium text-slate-300 block mb-1">
                    Landmark or Boarding Notes (Optional)
                  </label>
                  <textarea
                    id="notes"
                    rows={2}
                    placeholder="e.g. Near the HP petrol pump, under the blue bus shelter..."
                    value={studentNotes}
                    onChange={(e) => setStudentNotes(e.target.value)}
                    className="w-full bg-slate-900 border border-slate-700 rounded-xl p-3 text-xs text-slate-200 placeholder:text-slate-500 focus:outline-none focus:border-blue-500"
                  />
                </div>

                {/* Submit Action */}
                <div className="pt-2">
                  <button
                    type="submit"
                    disabled={submitting || !proposedLocation}
                    className="w-full inline-flex items-center justify-center gap-2 px-6 py-3 bg-blue-600 hover:bg-blue-500 disabled:opacity-50 text-white text-xs font-bold rounded-xl transition shadow-lg shadow-blue-900/30"
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
