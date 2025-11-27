import React from "react";

export const WerewolfIllustration = ({ className }: { className?: string }) => (
  <svg viewBox="0 0 200 200" className={className} fill="none" xmlns="http://www.w3.org/2000/svg">
    {/* Background Circle (Night Sky) */}
    <circle cx="100" cy="100" r="90" className="fill-slate-900" />
    
    {/* Moon */}
    <circle cx="140" cy="60" r="25" className="fill-yellow-100" />
    <circle cx="130" cy="55" r="4" className="fill-yellow-200/50" />
    <circle cx="150" cy="70" r="6" className="fill-yellow-200/50" />
    
    {/* Clouds */}
    <path d="M20 120C20 120 40 110 60 115C80 120 90 110 110 115" stroke="#334155" strokeWidth="8" strokeLinecap="round" />
    <path d="M140 130C140 130 160 120 180 125" stroke="#334155" strokeWidth="8" strokeLinecap="round" />

    {/* Wolf Silhouette */}
    <path d="M60 190V140C60 140 65 110 90 100C115 90 110 60 105 50C100 40 120 30 130 50C140 70 135 90 150 100C165 110 160 140 160 140V190H60Z" className="fill-slate-800" />
    
    {/* Eyes */}
    <path d="M115 65L125 70L115 75Z" className="fill-red-500" />
    
    {/* Ground */}
    <path d="M10 190H190" stroke="#1e293b" strokeWidth="10" strokeLinecap="round" />
  </svg>
);

export const UndercoverIllustration = ({ className }: { className?: string }) => (
  <svg viewBox="0 0 200 200" className={className} fill="none" xmlns="http://www.w3.org/2000/svg">
    {/* Background Circle */}
    <circle cx="100" cy="100" r="90" className="fill-indigo-50" />
    
    {/* Cards / People */}
    <rect x="40" y="60" width="40" height="60" rx="4" className="fill-white stroke-indigo-200" strokeWidth="2" />
    <rect x="120" y="60" width="40" height="60" rx="4" className="fill-white stroke-indigo-200" strokeWidth="2" />
    <rect x="80" y="50" width="40" height="60" rx="4" className="fill-indigo-100 stroke-indigo-300" strokeWidth="2" />
    
    {/* Question Mark on center card */}
    <text x="100" y="90" textAnchor="middle" className="fill-indigo-500 font-bold text-3xl" style={{ font: 'bold 30px sans-serif' }}>?</text>

    {/* Magnifying Glass Handle */}
    <path d="M120 130L150 160" stroke="#334155" strokeWidth="12" strokeLinecap="round" />
    
    {/* Magnifying Glass Rim */}
    <circle cx="100" cy="110" r="40" className="stroke-slate-700 fill-blue-400/20" strokeWidth="6" />
    
    {/* Reflection on Glass */}
    <path d="M80 90Q90 80 105 85" stroke="white" strokeWidth="3" strokeLinecap="round" className="opacity-60" />
  </svg>
);

export const TurtleSoupIllustration = ({ className }: { className?: string }) => (
  <svg viewBox="0 0 200 200" className={className} fill="none" xmlns="http://www.w3.org/2000/svg">
    <circle cx="100" cy="100" r="90" className="fill-emerald-50" />
    <path d="M50 100C50 100 60 60 100 60C140 60 150 100 150 100" className="fill-emerald-200 stroke-emerald-400" strokeWidth="4" />
    <path d="M50 100H150" className="stroke-emerald-400" strokeWidth="4" />
    <rect x="70" y="110" width="60" height="10" rx="2" className="fill-emerald-800" />
    <path d="M80 70L120 70" stroke="white" strokeWidth="4" strokeLinecap="round" />
    <path d="M90 85L110 85" stroke="white" strokeWidth="4" strokeLinecap="round" />
  </svg>
);