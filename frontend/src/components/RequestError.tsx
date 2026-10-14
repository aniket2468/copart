export function RequestError({message,onRetry}:{message:string;onRetry?:()=>void}) {
  return <div role="alert" className="rounded-xl border border-rose-200 bg-rose-50 p-4 text-sm text-rose-700">
    <div>{message}</div>
    {onRetry && <button className="mt-3 font-semibold underline" onClick={onRetry}>Try again</button>}
  </div>;
}
