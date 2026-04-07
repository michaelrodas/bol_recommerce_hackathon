import React, { useState, useRef, useEffect } from 'react';
import './App.css';

interface Message {
  id: string;
  text: string;
  sender: 'user' | 'ai';
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
  const messagesEndRef = useRef<HTMLDivElement>(null);

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  };

  useEffect(() => {
    scrollToBottom();
  }, [messages]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!input.trim()) return;

    const userMessage: Message = {
      id: Date.now().toString(),
      text: input.trim(),
      sender: 'user',
    };

    setMessages((prev) => [...prev, userMessage]);
    setInput('');
    setIsLoading(true);

    try {
      // Use relative path to leverage the Vite proxy and avoid CORS
      const response = await fetch('/api/chat', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ question: userMessage.text }),
      });

      if (!response.ok) {
        throw new Error(`API error: ${response.statusText}`);
      }

      const data = await response.json();

      const aiResponse: Message = {
        id: Date.now().toString(),
        text: data.answer || data.response || data.message || "I couldn't find an answer.",
        sender: 'ai',
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
              {message.text}
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
        <form onSubmit={handleSubmit} className="input-form">
          <input
            type="text"
            value={input}
            onChange={(e) => setInput(e.target.value)}
            placeholder="Ask a question about warehouse returns..."
            className="chat-input"
            disabled={isLoading}
          />
          <button type="submit" disabled={!input.trim() || isLoading} className="send-button">
            Send
          </button>
        </form>
      </footer>
    </div>
  );
}

export default App;
