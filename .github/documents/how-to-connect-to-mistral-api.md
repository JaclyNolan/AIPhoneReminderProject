✅ Getting started

You need an account on their platform (often referred to as “La Plateforme”). 
docs.mistral.ai
+1

After signing in, set up billing/workspace (even if you use a free/exploratory tier). 
docs.mistral.ai
+1

Then generate an API key. 
merge.dev
+1

The base endpoint is something like https://api.mistral.ai/v1/… for many calls. 
postman.com
+1

🛠 Core endpoints & usage
Chat / text completions

Endpoint: POST /v1/chat/completions with JSON body including model, messages (roles: “user”, “system”, etc) 
docs.mistral.ai
+2
postman.com
+2

Example in Python:

from mistralai import Mistral
client = Mistral(api_key="YOUR_KEY")
res = client.chat.complete(
    model="mistral-small-latest",
    messages=[
      {"role":"user","content":"Who is the best French painter? Answer in one short sentence."}
    ]
)
print(res)


docs.mistral.ai

Example via curl:

curl https://api.mistral.ai/v1/chat/completions \
 -H 'Authorization: Bearer YOUR_APIKEY' \
 -H 'Content-Type: application/json' \
 -d '{
      "model":"mistral-large-latest",
      "messages":[{"role":"user","content":"Who is the best French painter?"}]
    }'


docs.mistral.ai

Other capabilities

Embeddings / text-embedding: They support embedding models as well. 
docs.litellm.ai
+1

Document AI / OCR / structured data extraction: They provide APIs to process documents (OCR), extract structured output, annotations. 
docs.mistral.ai
+1

For example: you can call an OCR model mistral-ocr-2505 or similar and get bounding‐boxes for text/figures in a document. 
Reddit
+1

Library / Document-management endpoints: For example, there is a “Document Library” connector: list libraries, list documents, create new library etc. 
docs.mistral.ai

📊 Rate limits, tiers, usage

There are usage tiers (free vs paid) with different limits (requests per second, tokens per minute/month). 
docs.mistral.ai
+1

Rate limits apply at the organization or workspace level. 
docs.mistral.ai

For production use you may need to move to a paid plan / higher tier. 
docs.mistral.ai

🎯 Best‐practices & tips

Store your API key securely; do not commit it to public repos. (General advice, but applies here.)

Use clear message roles: “system” (for guiding the model), “user” for user input, “assistant” for expected assistant messages.

If using document OCR + Q&A workflows: Rather than repeatedly calling the OCR endpoint for the same document, extract once and reuse. For example, from Reddit:

“Run the pdf through v1/ocr once, grab the extracted text … then pipe the saved text into v1/agents/completions …” 
Reddit

Be aware of token usage: large documents or OCR’ed text may consume many tokens (cost) and affect limits.

Check model IDs: Make sure you are using correct model name (e.g., "mistral-small-latest", "mistral-large-latest") and available to your workspace. 
docs.litellm.ai
+1

Monitor usage: Which model you choose affects cost, speed, behavior.

Validate error handling: 401 unauthenticated, 429 rate limit, 400 bad request – typical API pitfalls. 
merge.dev
+1

🧮 Quick example for you (since you are a full‐stack dev)

Here’s a minimal workflow you might integrate into a backend endpoint (e.g., in your Vue3 + backend stack):

In your backend (Node.js or Python), store the environment variable MISTRAL_API_KEY.

Create a function to call the chat endpoint:

// Node.js example using fetch
async function askMistral(question) {
  const response = await fetch("https://api.mistral.ai/v1/chat/completions", {
    method: "POST",
    headers: {
      "Authorization": `Bearer ${process.env.MISTRAL_API_KEY}`,
      "Content-Type": "application/json"
    },
    body: JSON.stringify({
      model: "mistral-medium-latest",
      messages: [
        { role: "user", content: question }
      ]
    })
  });
  const data = await response.json();
  return data.choices[0].message.content;
}


In your front‐end (Vue3), you call your backend endpoint (e.g., /api/ask) which uses the above function and returns result to your UI.

If you need to process documents (e.g., your side‐project with teammates finding teammates might involve document uploads or extraction), you could call the OCR or document library endpoints. For example: upload PDF → call OCR → extract text → store in DB → call chat/completions for queries.

Monitor your workspace’s token usage and rate limits (via the Mistral console). If you hit limits, implement retry/backoff or upgrade tier.