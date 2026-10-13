const base=import.meta.env.VITE_API_URL??'';
export class ApiError extends Error{status:number;fields?:Record<string,string>;constructor(message:string,status:number,fields?:Record<string,string>){super(message);this.status=status;this.fields=fields}}
export async function api<T>(path:string,options:RequestInit={}):Promise<T>{const token=localStorage.getItem('pf_token');const response=await fetch(`${base}${path}`,{...options,headers:{'Content-Type':'application/json',...(token?{Authorization:`Bearer ${token}`} :{}),...options.headers}});if(response.status===401&&token){localStorage.removeItem('pf_token');localStorage.removeItem('pf_user');location.href='/login'}if(!response.ok){const data=await response.json().catch(()=>({}));throw new ApiError(data.message??'Something went wrong',response.status,data.fields)}return response.status===204?undefined as T:response.json()}
export const fmtDate=(d:string)=>new Intl.DateTimeFormat('en-US',{month:'short',day:'numeric',year:'numeric'}).format(new Date(`${d}T00:00:00`));
export const initials=(a:string,b:string)=>`${a[0]??''}${b[0]??''}`.toUpperCase();

