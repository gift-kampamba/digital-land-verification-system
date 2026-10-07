// web3.js — system-based digital signing for government workflow

function toHex(bytes) {
  return Array.from(bytes)
    .map((b) => b.toString(16).padStart(2, '0'))
    .join('');
}

export async function generateSystemSignature(payload) {
  const json = typeof payload === 'string' ? payload : JSON.stringify(payload, Object.keys(payload).sort());
  const encoder = new TextEncoder();
  const data = encoder.encode(json);
  const hashBuffer = await crypto.subtle.digest('SHA-256', data);
  const digest = toHex(new Uint8Array(hashBuffer));

  const timestamp = new Date().toISOString();
  const signatureId = `SIG-${Date.now()}`;

  return {
    signatureId,
    signedAt: timestamp,
    digest,
    value: `${signatureId}:${digest}`
  };
}

export async function signTransferMessage(parcelNumber, buyerAddress, actorName = 'System User') {
  const payload = {
    actorName,
    action: 'TRANSFER_APPROVAL',
    parcelNumber,
    buyerAddress,
    signedAt: new Date().toISOString()
  };

  const result = await generateSystemSignature(payload);
  return `${result.signatureId}:${result.digest}`;
}

export async function switchToHardhatNetwork() {
  return null;
}
