declare module 'sockjs-client/dist/sockjs.min.js' {
  import SockJS from 'sockjs-client'
  export default SockJS
}

declare module 'sockjs-client' {
  class SockJS {
    constructor(url: string, protocols?: string | string[], options?: SockJS.Options)
    close(code?: number, reason?: string): void
    send(data: string | ArrayBuffer | Blob | ArrayBufferView): void
    onopen: ((ev: Event) => void) | null
    onclose: ((ev: CloseEvent) => void) | null
    onmessage: ((ev: MessageEvent) => void) | null
    onerror: ((ev: Event) => void) | null
    readyState: number
    protocol: string
    url: string
  }

  namespace SockJS {
    interface Options {
      server_info?: object
      devel?: boolean
      debug?: boolean
      protocols_whitelist?: string[]
      info?: { websocket: boolean; cookie_needed: boolean; origins: string[]; url: string }
    }
  }

  export = SockJS
}
