import {createContext,useContext,useEffect,useState,type ReactNode} from 'react';
import {api,ApiError} from '../lib/api';
import type {Employee} from '../types';
type Ctx={user:Employee|null;loading:boolean;sessionError:string;retrySession:()=>void;login:(email:string,password:string)=>Promise<void>;logout:()=>void;refresh:()=>Promise<void>};
const AuthContext=createContext<Ctx|null>(null);
export function AuthProvider({children}:{children:ReactNode}){
  const [user,setUser]=useState<Employee|null>(null);
  const [sessionToken,setSessionToken]=useState(()=>localStorage.getItem('pf_token'));
  const [loading,setLoading]=useState(()=>Boolean(localStorage.getItem('pf_token')));
  const [sessionError,setSessionError]=useState('');
  const [attempt,setAttempt]=useState(0);
  useEffect(()=>{
    if(!sessionToken){localStorage.removeItem('pf_user');return}
    const controller=new AbortController();
    api<Employee>('/api/employees/me',{signal:controller.signal})
      .then(u=>{if(!controller.signal.aborted){localStorage.setItem('pf_user',JSON.stringify(u));setUser(u)}})
      .catch(error=>{
        if(controller.signal.aborted)return;
        if(error instanceof ApiError&&error.status===401){localStorage.removeItem('pf_token');localStorage.removeItem('pf_user');setUser(null)}
        else setSessionError(error instanceof Error?error.message:'Unable to verify your session');
      }).finally(()=>{if(!controller.signal.aborted)setLoading(false)});
    return ()=>controller.abort();
  },[attempt,sessionToken]);
  function retrySession(){setSessionError('');setLoading(true);setAttempt(value=>value+1)}
  async function login(email:string,password:string){const r=await api<{token:string;user:Employee}>('/api/auth/login',{method:'POST',body:JSON.stringify({email,password})});localStorage.setItem('pf_token',r.token);localStorage.setItem('pf_user',JSON.stringify(r.user));setSessionToken(r.token);setSessionError('');setUser(r.user)}
  function logout(){localStorage.removeItem('pf_token');localStorage.removeItem('pf_user');setSessionToken(null);setUser(null);setSessionError('')}
  async function refresh(){const u=await api<Employee>('/api/employees/me');localStorage.setItem('pf_user',JSON.stringify(u));setUser(u)}
  return <AuthContext.Provider value={{user,loading,sessionError,retrySession,login,logout,refresh}}>{children}</AuthContext.Provider>
}
export const useAuth=()=>{const c=useContext(AuthContext);if(!c)throw new Error('Missing AuthProvider');return c};
