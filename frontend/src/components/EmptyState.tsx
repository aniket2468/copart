import {Inbox} from 'lucide-react';
export function EmptyState({title='Nothing here yet',description='New activity will appear here.'}:{title?:string;description?:string}){return <div className="flex flex-col items-center px-6 py-14 text-center"><div className="mb-4 rounded-2xl bg-slate-100 p-3 text-slate-500"><Inbox size={24}/></div><h3 className="font-semibold">{title}</h3><p className="mt-1 max-w-sm text-sm text-slate-500">{description}</p></div>}

