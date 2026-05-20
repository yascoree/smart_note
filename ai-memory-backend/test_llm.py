from transformers import pipeline

print("Loading model...")

generator = pipeline(
    "text-generation",
    model="TinyLlama/TinyLlama-1.1B-Chat-v1.0"
)

print("Model loaded!")

prompt = """
Answer this question:

What is FastAPI?
"""

result = generator(
    prompt,
    max_length=50
)

print(result[0]['generated_text'])