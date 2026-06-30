import React, { useState, useRef, useEffect } from 'react';
import './App.css';

interface Message {
  id: string;
  text: string;
  sender: 'user' | 'ai';
  imageUrl?: string;
  sources?: string[];
  responseTimeMs?: number;
}

function App() {
  const [messages, setMessages] = useState<Message[]>([
    {
      id: 'welcome',
      text: 'Hello Operator! I am your Returns Domain Assistant. How can I help you with warehouse returns today?',
      sender: 'ai',
    },
  ]);
  const [input, setInput] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [imageFile, setImageFile] = useState<File | null>(null);
  const [imagePreviewUrl, setImagePreviewUrl] = useState<string | null>(null);
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  };

  useEffect(() => {
    scrollToBottom();
  }, [messages]);

  const handleImageSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    if (imagePreviewUrl) URL.revokeObjectURL(imagePreviewUrl);
    setImageFile(file);
    setImagePreviewUrl(URL.createObjectURL(file));
    e.target.value = '';
  };

  const clearImage = () => {
    if (imagePreviewUrl) URL.revokeObjectURL(imagePreviewUrl);
    setImageFile(null);
    setImagePreviewUrl(null);
  };

  const canSubmit = input.trim().length > 0 || imageFile !== null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!canSubmit || isLoading) return;

    const questionText = input.trim() || 'Please assess this item based on the provided image.';
    const sentImageUrl = imagePreviewUrl ?? undefined;

    const userMessage: Message = {
      id: Date.now().toString(),
      text: questionText,
      sender: 'user',
      imageUrl: sentImageUrl,
    };

    setMessages((prev) => [...prev, userMessage]);
    setInput('');
    setImageFile(null);
    setImagePreviewUrl(null);
    setIsLoading(true);

    try {
      const formData = new FormData();
      formData.append('question', questionText);
      if (imageFile) {
        formData.append('image', imageFile);
      }

      const response = await fetch('/api/chat', {
        method: 'POST',
        body: formData,
      });

      if (!response.ok) {
        throw new Error(`API error: ${response.statusText}`);
      }

      const data = await response.json();

      const aiResponse: Message = {
        id: Date.now().toString(),
        text: data.answer || data.response || data.message || "I couldn't find an answer.",
        sender: 'ai',
        sources: data.sources || [],
        responseTimeMs: data.responseTimeMs,
      };

      setMessages((prev) => [...prev, aiResponse]);
    } catch (error) {
      console.error('Error calling backend API:', error);
      const errorResponse: Message = {
        id: Date.now().toString(),
        text: "Sorry, I encountered an error connecting to the server.",
        sender: 'ai',
      };
      setMessages((prev) => [...prev, errorResponse]);
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="app-container">
      <header className="chat-header">
        <h1>Returns Domain Assistant</h1>
      </header>

      <main className="chat-container">
        {messages.map((message) => (
          <div key={message.id} className={`message-wrapper ${message.sender}`}>
            <div className={`message ${message.sender}`}>
              {message.imageUrl && (
                <img src={message.imageUrl} alt="Attached" className="message-image" />
              )}
              <div className="message-text">{message.text}</div>

              {message.sources && message.sources.length > 0 && (
                <div className="message-sources">
                  <strong>Sources:</strong>
                  <ul>
                    {message.sources.map((source, index) => (
                      <li key={index}>{source}</li>
                    ))}
                  </ul>
                </div>
              )}

              {message.responseTimeMs && (
                <div className="message-timing">
                  Generated in {(message.responseTimeMs / 1000).toFixed(2)}s
                </div>
              )}
            </div>
          </div>
        ))}
        {isLoading && (
          <div className="message-wrapper ai">
            <div className="message ai typing-indicator">
              <span></span><span></span><span></span>
            </div>
          </div>
        )}
        <div ref={messagesEndRef} />
      </main>

      <footer className="input-container">
        {imagePreviewUrl && (
          <div className="image-preview-container">
            <img src={imagePreviewUrl} alt="Preview" className="image-preview" />
            <button type="button" onClick={clearImage} className="image-preview-remove" title="Remove image">
              ×
            </button>
          </div>
        )}
        <form onSubmit={handleSubmit} className="input-form">
          <input
            type="file"
            accept="image/*"
            ref={fileInputRef}
            onChange={handleImageSelect}
            className="file-input-hidden"
          />
          <button
            type="button"
            onClick={() => fileInputRef.current?.click()}
            className="attach-button"
            disabled={isLoading}
            title="Attach an image"
          >
            📎
          </button>
          <input
            type="text"
            value={input}
            onChange={(e) => setInput(e.target.value)}
            placeholder="Ask a question or attach an image of the item..."
            className="chat-input"
            disabled={isLoading}
          />
          <button type="submit" disabled={!canSubmit || isLoading} className="send-button">
            Send
          </button>
        </form>
      </footer>
    </div>
  );
}

export default App;
