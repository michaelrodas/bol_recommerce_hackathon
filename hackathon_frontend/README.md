# Returns Domain AI Assistant (Frontend)

Welcome to the frontend project for the Returns Domain AI Assistant! This project was built to provide a clean, simple, and easy-to-use chat interface—much like Claude or ChatGPT—specifically designed for asking questions about warehouse returns.

## How This Project Started

This project was initially created as a standard template (using Vite and React). The goal was to transform it into a functional chat website. 

**The original request that started this transformation was:**

> "This project was scaffolded by using Vite, it is a React project and I want to use it for creating a chat website that looks like Claude, it will be used for inputting question prompts into an AI system that has knowledge about returns domain in a warehouse; my knowledge about frontend is limited as I am a backend developer, make the code simple and understandable and follow to the best development practices."

## What We Did

To fulfill this request and make the code easy for a backend developer to work with, the following changes were made:

1. **Cleaned Up the Template:** 
   We removed all the default logos, spinning graphics, and boilerplate code that came with the original setup.

2. **Built a Chat Interface:** 
   We created a clean layout that takes up the whole screen. It features:
   - A header at the top.
   - A scrollable chat history in the middle.
   - An input box at the bottom for typing questions.

3. **Added Chat Bubbles & Animations:** 
   User messages and AI responses are styled differently so it's easy to read the conversation. We also added a helpful "typing..." animation (three blinking dots) to show when the AI is thinking.

4. **Prepared for the Backend:** 
   Inside the main code file (`src/App.tsx`), we set up a "mock" or "fake" AI response. When you type a message and hit Send, the system waits for 1 second and then replies with a placeholder message. 
   - **Next Step for Developers:** There is a specific note in the code showing exactly where to connect this frontend to the real AI backend system.

## How to Run It

Follow these simple steps to run the chat interface on your computer:

1. **Open your terminal or command prompt.**
2. **Navigate to the project folder.** Make sure you are inside the `hackathon_frontend` folder.
3. **Install the dependencies.** Type the following command and press Enter:
   ```bash
   npm install
   ```
   *This downloads all the necessary pieces to make the website work.*
4. **Start the website.** Type the following command and press Enter:
   ```bash
   npm run dev
   ```
5. **Open your browser.** Once the website starts, it will give you a link in the terminal (usually `http://localhost:5173`). Click on that link or type it into your web browser (like Chrome, Firefox, or Safari).

You can now test the chat interface and see how it looks!
