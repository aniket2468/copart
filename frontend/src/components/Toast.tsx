import {CheckCircle2,X} from 'lucide-react';
export function Toast({message,onClose}:{message:string;onClose:()=>void}){return <div className="fixed bottom-5 right-5 z-50 flex max-w-sm items-center gap-3 rounded-xl bg-ink px-4 py-3 text-sm text-white shadow-2xl"><CheckCircle2 className="text-emerald-400" size={19}/><span>{message}</span><button onClick={onClose} aria-label="Dismiss"><X size={17}/></button></div>}

