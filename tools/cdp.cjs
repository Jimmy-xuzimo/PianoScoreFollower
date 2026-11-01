// Evaluate a diagnostic expression against the app's forwarded debug WebView.
(async () => {
    try {
        const targets = await (await fetch('http://127.0.0.1:9222/json')).json();
        const ws = new WebSocket(targets.find(t => t.type === 'page').webSocketDebuggerUrl);
        await new Promise((resolve,reject) => {ws.onopen=resolve;ws.onerror=reject;});
        let sequence=0;
        const pending=new Map();
        ws.onmessage=e=>{const v=JSON.parse(e.data);if(pending.has(v.id)){pending.get(v.id)(v);pending.delete(v.id);}};
        function evaluate(expression) {
            const id=++sequence;
            return new Promise(resolve=>{
                pending.set(id,resolve);
                ws.send(JSON.stringify({id,method:'Runtime.evaluate',params:{expression,returnByValue:true,awaitPromise:true}}));
            });
        }
        for(let attempt=0;attempt<100;attempt++) {
            const ready=await evaluate('typeof window.PianoScoreViewer !== "undefined"');
            if(ready.result?.result?.value) break;
            await new Promise(resolve=>setTimeout(resolve,100));
        }
        console.log(JSON.stringify(await evaluate(process.argv[2])));ws.close();
    } catch (error) { console.error(error.message); process.exitCode=1; }
})();
